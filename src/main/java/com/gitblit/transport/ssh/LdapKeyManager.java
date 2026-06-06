/*
 * Copyright 2016 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.GenericUtils;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.ldap.LdapConnection;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;

public class LdapKeyManager extends IPublicKeyManager {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^([^\":]+):");
    private static final Pattern GB_PERM_PATTERN = Pattern.compile("(?i:gbPerm)\\s*=\\s*(?:\\\\\"|\")?\\s*([A-Za-z+-]+)");

    private final IStoredSettings settings;

    @Inject
    public LdapKeyManager(IStoredSettings settings) {
        this.settings = settings;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public LdapKeyManager start() {
        log.info(toString());
        return this;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public LdapKeyManager stop() {
        return this;
    }

    @Override
    protected boolean isStale(String username) {
        return true;
    }

    @Override
    protected List<SshKey> getKeysImpl(String username) {
        try (LdapConnection conn = new LdapConnection(settings)) {
            if (conn.connect()) {
                log.info("loading ssh key for {} from LDAP directory", username);

                BindResult bindResult = conn.bind();
                if (bindResult == null) {
                    conn.close();
                    return null;
                }

                String pubKeyAttribute = settings.getString(Keys.realm.ldap.sshPublicKey, "sshPublicKey");
                String pkaPrefix = null;
                int idx = pubKeyAttribute.indexOf(':');
                if (idx > 0) {
                    pkaPrefix = pubKeyAttribute.substring(idx + 1);
                    pubKeyAttribute = pubKeyAttribute.substring(0, idx);
                }

                SearchResult result = conn.searchUser(getSimpleUsername(username), Arrays.asList(pubKeyAttribute));
                conn.close();

                if (result != null && result.getResultCode() == ResultCode.SUCCESS) {
                    if (result.getEntryCount() > 1) {
                        log.info("Found more than one entry for user {} in LDAP. Cannot retrieve SSH key.", username);
                        return null;
                    } else if (result.getEntryCount() < 1) {
                        log.info("Found no entry for user {} in LDAP. Cannot retrieve SSH key.", username);
                        return null;
                    }

                    SearchResultEntry foundUser = result.getSearchEntries().get(0);
                    String[] attrs = foundUser.getAttributeValues(pubKeyAttribute);
                    if (attrs == null || attrs.length == 0) {
                        log.info("found no keys for user {} under attribute {} in directory", username, pubKeyAttribute);
                        return null;
                    }

                    List<GbAuthorizedKeyEntry> authorizedKeys = new ArrayList<GbAuthorizedKeyEntry>(attrs.length);
                    Matcher m = PREFIX_PATTERN.matcher("");
                    for (int i = 0; i < attrs.length; ++i) {
                        String keyEntry = Joiner.on("").join(attrs[i].replace("\r\n", "\n").split("\n"));
                        m.reset(keyEntry);
                        try {
                            if (m.lookingAt()) {
                                if (pkaPrefix == null) {
                                    continue;
                                }
                                String prefix = m.group(1).trim();
                                if (!pkaPrefix.equalsIgnoreCase(prefix)) {
                                    continue;
                                }
                                authorizedKeys.add(GbAuthorizedKeyEntry.parseAuthorizedKeyEntry(keyEntry.substring(m.end())));
                            } else {
                                if (pkaPrefix != null) {
                                    continue;
                                }
                                authorizedKeys.add(GbAuthorizedKeyEntry.parseAuthorizedKeyEntry(keyEntry));
                            }
                        } catch (IllegalArgumentException e) {
                            log.info("Failed to parse key entry={}:", keyEntry, e.getMessage());
                        }
                    }

                    List<SshKey> keyList = new ArrayList<SshKey>(authorizedKeys.size());
                    for (GbAuthorizedKeyEntry keyEntry : authorizedKeys) {
                        try {
                            SshKey key = new SshKey(keyEntry.resolvePublicKey(null, null));
                            key.setComment(keyEntry.getComment());
                            setKeyPermissions(key, keyEntry);
                            keyList.add(key);
                        } catch (GeneralSecurityException | IOException e) {
                            log.warn("Error resolving key entry for user {}. Entry={}", username, keyEntry, e);
                        }
                    }
                    return keyList;
                }
            }
        }

        return null;
    }

    @Override
    public boolean addKey(String username, SshKey key) {
        return false;
    }

    @Override
    public boolean removeKey(String username, SshKey key) {
        return false;
    }

    @Override
    public boolean removeAllKeys(String username) {
        return false;
    }

    public boolean supportsWritingKeys(UserModel user) {
        return false;
    }

    public boolean supportsCommentChanges(UserModel user) {
        return false;
    }

    public boolean supportsPermissionChanges(UserModel user) {
        return false;
    }

    private void setKeyPermissions(SshKey key, GbAuthorizedKeyEntry keyEntry) {
        List<String> env = keyEntry.getLoginOptionValues("environment");
        if (env != null && !env.isEmpty()) {
            for (String envi : env) {
                Matcher m = GB_PERM_PATTERN.matcher(envi);
                if (m.find()) {
                    String perm = m.group(1).trim();
                    AccessPermission ap = AccessPermission.fromCode(perm);
                    if (ap == AccessPermission.NONE) {
                        ap = AccessPermission.valueOf(perm.toUpperCase());
                    }

                    if (ap != null && ap != AccessPermission.NONE) {
                        try {
                            key.setPermission(ap);
                        } catch (IllegalArgumentException e) {
                            log.warn("Incorrect permissions ({}) set for SSH key entry {}.", ap, envi, e);
                        }
                    }
                }
            }
        }
    }

    private String getSimpleUsername(String username) {
        int lastSlash = username.lastIndexOf('\\');
        if (lastSlash > -1) {
            username = username.substring(lastSlash + 1);
        }

        return username;
    }

    private static class GbAuthorizedKeyEntry extends AuthorizedKeyEntry {

        private static final long serialVersionUID = 1L;
        private static final Pattern LEADIN_PATTERN = Pattern.compile("^((?:[^\\s\"]*|(?:\"(?:[^\"\\\\]|\\\\\"|\\\\)*\"))*\\s+)(.+)");
        private static final Pattern OPTION_PATTERN = Pattern.compile("([^\",]+|(?:\"(?:[^\"\\\\]|\\\\\"|\\\\)*\"))+");

        private Map<String, List<String>> loginOptionsMulti = Collections.emptyMap();

        List<String> getLoginOptionValues(String option) {
            return loginOptionsMulti.get(option);
        }

        public static GbAuthorizedKeyEntry parseAuthorizedKeyEntry(String line) throws IllegalArgumentException {
            line = GenericUtils.trimToEmpty(line);
            if (StringUtils.isEmpty(line) || (line.charAt(0) == COMMENT_CHAR)) {
                return null;
            }

            Matcher m = LEADIN_PATTERN.matcher(line);
            if (!m.lookingAt()) {
                throw new IllegalArgumentException("Bad format (no key data delimiter): " + line);
            }

            String keyType = m.group(1).trim();
            final GbAuthorizedKeyEntry entry;
            if (KeyUtils.getPublicKeyEntryDecoder(keyType) == null) {
                entry = parseAuthorizedKeyEntry(m.group(2));
                if (entry == null) {
                    throw new IllegalArgumentException("Bad format (no key data after login options): " + line);
                }

                entry.parseAndSetLoginOptions(keyType);
            } else {
                int startPos = line.indexOf(' ');
                if (startPos <= 0) {
                    throw new IllegalArgumentException("Bad format (no key data delimiter): " + line);
                }

                int endPos = line.indexOf(' ', startPos + 1);
                if (endPos <= startPos) {
                    endPos = line.length();
                }

                String encData = (endPos < (line.length() - 1)) ? line.substring(0, endPos).trim() : line;
                String comment = (endPos < (line.length() - 1)) ? line.substring(endPos + 1).trim() : null;
                entry = parsePublicKeyEntry(new GbAuthorizedKeyEntry(), encData);
                entry.setComment(comment);
            }

            return entry;
        }

        private void parseAndSetLoginOptions(String options) {
            Matcher m = OPTION_PATTERN.matcher(options);
            if (!m.find()) {
                loginOptionsMulti = Collections.emptyMap();
                return;
            }
            Map<String, List<String>> optsMap = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);

            do {
                String p = m.group();
                p = GenericUtils.trimToEmpty(p);
                if (StringUtils.isEmpty(p)) {
                    continue;
                }

                int pos = p.indexOf('=');
                String name = (pos < 0) ? p : GenericUtils.trimToEmpty(p.substring(0, pos));
                CharSequence value = (pos < 0) ? null : GenericUtils.trimToEmpty(p.substring(pos + 1));
                value = GenericUtils.stripQuotes(value);

                if (value == null) {
                    value = Boolean.TRUE.toString();
                }

                List<String> opts = optsMap.get(name);
                if (opts == null) {
                    opts = new ArrayList<String>();
                    optsMap.put(name, opts);
                }
                opts.add(value.toString());
            } while (m.find());

            loginOptionsMulti = optsMap;
        }
    }
}

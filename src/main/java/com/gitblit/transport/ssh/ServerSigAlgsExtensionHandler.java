/*
 * Copyright 2026 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.transport.ssh;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.kex.extension.KexExtensionHandler;
import org.apache.sshd.common.kex.extension.KexExtensions;
import org.apache.sshd.common.kex.extension.parser.ServerSignatureAlgorithms;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.logging.AbstractLoggingBean;

public class ServerSigAlgsExtensionHandler extends AbstractLoggingBean implements KexExtensionHandler {

    public static final AttributeKey<Boolean> CLIENT_REQUESTED_EXT_INFO = new AttributeKey<>();
    public static final AttributeKey<Boolean> EXT_INFO_SENT_AT_NEWKEYS = new AttributeKey<>();

    @Override
    public void handleKexInitProposal(Session session, boolean initiator, Map<KexProposalOption, String> proposal)
            throws Exception {
        if (initiator) {
            return;
        }

        if (session.getAttribute(CLIENT_REQUESTED_EXT_INFO) != null) {
            return;
        }

        String algorithms = proposal.get(KexProposalOption.ALGORITHMS);
        boolean clientWantsExtInfo = Arrays.asList(GenericUtils.split(algorithms, ','))
                .contains(KexExtensions.CLIENT_KEX_EXTENSION);
        session.setAttribute(CLIENT_REQUESTED_EXT_INFO, Boolean.valueOf(clientWantsExtInfo));
        if (clientWantsExtInfo && log.isTraceEnabled()) {
            log.trace("handleKexInitProposal({}): got ext-info-c from client", session);
        }
    }

    @Override
    public boolean isKexExtensionsAvailable(Session session, AvailabilityPhase phase) {
        return phase != AvailabilityPhase.PREKEX;
    }

    @Override
    public void sendKexExtensions(Session session, KexPhase phase) throws Exception {
        if (phase == KexPhase.NEWKEYS) {
            Boolean alreadySent = session.getAttribute(EXT_INFO_SENT_AT_NEWKEYS);
            if (Boolean.TRUE.equals(alreadySent)) {
                return;
            }
            session.setAttribute(EXT_INFO_SENT_AT_NEWKEYS, Boolean.TRUE);
        }

        Boolean doExtInfo = session.getAttribute(CLIENT_REQUESTED_EXT_INFO);
        if (!Boolean.TRUE.equals(doExtInfo)) {
            if (log.isTraceEnabled()) {
                log.trace("sendKexExtensions({})[{}]: client did not send ext-info-c; skipping SSH_MSG_EXT_INFO",
                        session, phase);
            }
            return;
        }

        Map<String, Object> extensions = new LinkedHashMap<>();
        collectExtensions(session, phase, extensions::put);
        if (extensions.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("sendKexExtensions({})[{}]: no extension info; skipping SSH_MSG_EXT_INFO", session, phase);
            }
            return;
        }

        Buffer buffer = session.createBuffer(KexExtensions.SSH_MSG_EXT_INFO);
        KexExtensions.putExtensions(extensions.entrySet(), buffer);
        session.writePacket(buffer);
        if (log.isDebugEnabled()) {
            log.debug("sendKexExtensions({})[{}]: sending SSH_MSG_EXT_INFO with {} info records",
                    session, phase, Integer.valueOf(extensions.size()));
        }
    }

    public void collectExtensions(Session session, KexPhase phase, BiConsumer<String, Object> marshaller) {
        if (phase != KexPhase.NEWKEYS) {
            return;
        }

        Collection<String> algorithms = session.getSignatureFactoriesNames();
        if (!GenericUtils.isEmpty(algorithms)) {
            marshaller.accept(ServerSignatureAlgorithms.NAME, algorithms);
            if (log.isDebugEnabled()) {
                log.debug("collectExtensions({})[{}]: extension info {}: {}", session, phase,
                        ServerSignatureAlgorithms.NAME, String.join(",", algorithms));
            }
        } else if (log.isWarnEnabled()) {
            log.warn("collectExtensions({})[{}]: extension info {} has no algorithms; skipping",
                    session, phase, ServerSignatureAlgorithms.NAME);
        }
    }
}

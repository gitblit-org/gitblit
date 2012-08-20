/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gitblit;

import com.gitblit.models.UserModel;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate authentication system based on SQL database.
 *
 * This class can enable user login capabilities using external database source
 * like MySQL, Oracle, .. and is really usefull  to integrate gitblit with
 * external tools that store users in DB (like Redmine, Drupal, .. and a lot of
 * other OpenSoure tools).
 *
 * This class is sponsored by Agavee GmbH {@link http://www.agavee.com}
 *
 * @author Marco Vito Moscaritolo <marco@agavee.com>
 */
public class SqlUserService extends GitblitUserService {
    private final Logger logger = LoggerFactory.getLogger(com.gitblit.SqlUserService.class);
    private IStoredSettings settings;

    public SqlUserService() {
        super();
    }

    @Override
    public void setup(IStoredSettings settings) {
	this.settings = settings;

        String file = settings.getString(Keys.realm.sql.backingUserService, "users.conf");
        File realmFile = GitBlit.getFileOrFolder(file);

        serviceImpl = createUserService(realmFile);
        logger.info("SQL User Service backed by " + serviceImpl.toString());
    }

    /**
     * Credentials are defined in the SQL server and can not be manipulated
     * from Gitblit.
     *
     * @return
     */
    @Override
    public boolean supportsCredentialChanges() {
        return false;
    }

    @Override
    public boolean supportsDisplayNameChanges() {
        return false;
    }

    @Override
    public boolean supportsEmailAddressChanges() {
        return false;
    }

    @Override
    public boolean supportsTeamMembershipChanges() {
        return false;
    }

    @Override
    public boolean supportsCookies() {
        return false;
    }

    @Override
    public UserModel authenticate(String username, char[] password) {
        UserModel user = null;
        Connection conn = null;
        PreparedStatement sql_stat = null;
        ResultSet rs = null;

        String SQL = this.settings.getString(Keys.realm.sql.selectUser, "SELECT name, password, mail FROM users WHERE name = ? AND password = ?");

        try {
            // Create DB connection
            conn = this.getDbConnection();

            // Create SQL statement
            sql_stat = conn.prepareStatement(SQL);

            // Add usename and password to select
            sql_stat.setString(1, username);
            sql_stat.setString(2, this.getDBPassword(password));

            // Execute SQL statement
            rs = sql_stat.executeQuery();

            while (rs.next()) {
                user = new UserModel(rs.getString("name"));
                user.emailAddress = rs.getString("mail");
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }
        finally {
            // Close DB connection
            try {
                rs.close();
                sql_stat.close();
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }

        return user;
    }

    /**
     * Get DB connection using data in settings.
     *
     * All settings are configurable in gitblit.properties file.
     *
     * @return Connection to specified database.
     *
     * @throws Exception
     */
    protected Connection getDbConnection() throws Exception {
        String url    = this.settings.getString(Keys.realm.sql.url,    "jdbc:mysql://localhost:3306/gitblit");
        String driver = this.settings.getString(Keys.realm.sql.driver, "com.mysql.jdbc.Driver");

        Properties properties = new Properties();
        properties.put("user",     this.settings.getString(Keys.realm.sql.username, ""));
        properties.put("password", this.settings.getString(Keys.realm.sql.password, ""));

        Class.forName(driver);

        return DriverManager.getConnection(url, properties);
    }

    /**
     * Get password in the DB format.
     *
     * This function return password converted to DB format. Add salt (before
     * and after the passord) if value as available into settings and also
     * encrypt using specified algorithm.
     *
     * @param password password from user input
     *
     * @return password stored in DB
     *
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    protected String getDBPassword(char[] password) {
        String salt_pre  = this.settings.getString(Keys.realm.sql.saltPre, "");
        String salt_post = this.settings.getString(Keys.realm.sql.saltPost, "");
        String algorithm = this.settings.getString(Keys.realm.sql.passwordStorage, "");

        String dbpws = salt_pre + String.valueOf(password) + salt_post;

        if (!algorithm.isEmpty()) {
            try {
                return this.hashDBPassword(dbpws, algorithm);
                // @TODO: catch multiple exceptions for Java 7
            } catch (NoSuchAlgorithmException e) {
                logger.error(e.getMessage());
            } catch (UnsupportedEncodingException e) {
                logger.error(e.getMessage());
            }
            return null;
        }
        else {
            return dbpws;
        }
    }

    /**
     * Crypt a string with specified algoritm
     *
     * @param password The password to hash
     * @param algorithm Algoritm to use for password hashing, can be MD2, MD5, SHA-1,
     *                  SHA-256, ... and all other params available for MessageDigest class.
     *                  {@link http://docs.oracle.com/javase/1.4.2/docs/guide/security/CryptoSpec.html#AppA}
     *
     * @return password hash
     *
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    protected String hashDBPassword(String password, String algorithm) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        final StringBuilder hash = new StringBuilder();
        final MessageDigest m = MessageDigest.getInstance(algorithm);

        m.update(password.getBytes("UTF-8"));

        final byte data[] = m.digest();
        for (byte element : data) {
            hash.append(Character.forDigit((element >> 4) & 0xf, 16));
            hash.append(Character.forDigit(element & 0xf, 16));
        }

        return hash.toString();
    }
}

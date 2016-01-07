/*
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
package com.gitblit.auth;

import com.gitblit.Constants;
import com.gitblit.Keys;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.PasswordUtils;

/**
 * Gitblit authentication provider with an SQLite backend
 * 
 * @author Glenn Matthys <glenn@webmind.be>
 */
public class SQLiteAuthProvider extends UsernamePasswordAuthenticationProvider {

    private Connection dbConn;

    /**
     * Instantiate a new SQLiteAuthProvider object
     */
    public SQLiteAuthProvider() {
        super("sqlite");
    }

    @Override
    public void setup() {
        this.dbConn = null;

        String connStr = "jdbc:sqlite:" + this.settings.getRequiredString(Keys.realm.sqlite.database);

        SQLiteConfig config = new SQLiteConfig();
        config.setOpenMode(SQLiteOpenMode.READONLY);

        try {
            Class.forName("org.sqlite.JDBC");
            this.dbConn = DriverManager.getConnection(connStr, config.toProperties());
        } catch (Exception ex) {
            System.err.println("Could not open SQLite database: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Override
    public UserModel authenticate(String username, char[] password) {

        if (this.dbConn == null) {
            logger.error("Failed to open database during setup phase, cannot authenticate user");
            return null;
        }

        String selectUserSQL = this.settings.getString(Keys.realm.sqlite.userPWQuery,
                "SELECT password FROM users WHERE username = ?");

        try {
            PreparedStatement selectUser = this.dbConn.prepareStatement(selectUserSQL);
            selectUser.setString(1, username);
            ResultSet rs = selectUser.executeQuery();

            if (!rs.next()) {
                logger.debug("User not found in database");
                return null;
            }

            String storedPwd = rs.getString(1);
            String challenge = new String(password);

            if (PasswordUtils.isApachePassValid(challenge, storedPwd, username)) {

                UserModel curr = userManager.getUserModel(username);
                UserModel user;
                if (curr == null) {
                    user = new UserModel(username);
                } else {
                    user = curr;
                }

                setCookie(user, password);

                user.password = Constants.EXTERNAL_ACCOUNT;
                user.accountType = getAccountType();

                updateUser(user);

                return user;
            }

        } catch (SQLException ex) {
            logger.error(String.format("SQL query failed: (%d) %s", ex.getErrorCode(), ex.getMessage()));
        }

        return null;
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.SQLITE;
    }

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
    public boolean supportsRoleChanges(UserModel user, Role role) {
        return false;
    }

    @Override
    public boolean supportsRoleChanges(TeamModel team, Role role) {
        return false;
    }
}

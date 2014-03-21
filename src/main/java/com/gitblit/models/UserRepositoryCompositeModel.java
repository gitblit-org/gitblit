package com.gitblit.models;

/*
 * Copyright 2011 gitblit.com.
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


import com.gitblit.utils.StringUtils;

import java.io.Serializable;

public class UserRepositoryCompositeModel implements Serializable {

    private static final long serialVersionUID = 1L;

    public UserModel userModel;
    public RepositoryModel repositoryModel;

    public UserModel getUserModel() {
        return userModel;
    }

    public void setUserModel(UserModel userModel) {
        this.userModel = userModel;
    }

    public RepositoryModel getRepositoryModel() {
        return repositoryModel;
    }

    public void setRepositoryModel(RepositoryModel repositoryModel) {
        this.repositoryModel = repositoryModel;
    }

}
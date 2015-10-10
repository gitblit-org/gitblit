/*
 * Copyright 2015 gitblit.com.
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
package com.gitblit.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A FilestoreModel represents a file stored outside a repository but referenced by the repository using a unique objectID
 * 
 * @author Paul Martin
 *
 */
public class FilestoreModel implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String oid;
	
	private Long size;
	private Status status;
	
	//Audit
	private String stateChangedBy;
	private Date stateChangedOn;

	//Access Control
	private List<String> repositories;
	
	public FilestoreModel(String id, long expectedSize, UserModel user, String repo) {
		oid = id;
		size = expectedSize;
		status = Status.Upload_Pending;
		stateChangedBy = user.getName();
		stateChangedOn = new Date();
		repositories = new ArrayList<String>();
		repositories.add(repo);
	}
	
	public synchronized long getSize() {
		return size;
	}
	
	public synchronized Status getStatus() {
		return status;
	}
	
	public synchronized String getChangedBy() {
		return stateChangedBy;
	}
	
	public synchronized Date getChangedOn() {
		return stateChangedOn;
	}
	
	public synchronized void setStatus(Status status, UserModel user) {
		this.status = status;
		stateChangedBy = user.getName();
		stateChangedOn = new Date();
	}
	
	public synchronized void reset(UserModel user, long size) {
		status = Status.Upload_Pending;
		stateChangedBy = user.getName();
		stateChangedOn = new Date();
		this.size = size;
	}
	
	/*
	 *  Handles possible race condition with concurrent connections
	 *  @return true if action can proceed, false otherwise
	 */
	public synchronized boolean actionUpload(UserModel user) {
		if (status == Status.Upload_Pending) {
			status = Status.Upload_In_Progress;
			stateChangedBy = user.getName();
			stateChangedOn = new Date();
			return true;
		}
		
		return false;
	}
	
	public synchronized boolean isInErrorState() {
		return (this.status.value < 0);
	}
	
	public synchronized void addRepository(String repo) {
		if (!repositories.contains(repo)) {
			repositories.add(repo);
		}	
	}
	
	public synchronized void removeRepository(String repo) {
		repositories.remove(repo);
	}
	
	public static enum Status {

		Deleted(-30),
		AuthenticationRequired(-20),
		
		Error_Unknown(-8),
		Error_Unexpected_Stream_End(-7),
		Error_Invalid_Oid(-6),
		Error_Invalid_Size(-5),
		Error_Hash_Mismatch(-4),
		Error_Size_Mismatch(-3), 
		Error_Exceeds_Size_Limit(-2),
		Error_Unauthorized(-1),
		//Negative values provide additional information and may be treated as 0 when not required
		Unavailable(0),
		Upload_Pending(1),
		Upload_In_Progress(2),
		Available(3);

		final int value;

		Status(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		@Override
		public String toString() {
			return name().toLowerCase().replace('_', ' ');
		}

		public static Status fromState(int state) {
			for (Status s : values()) {
				if (s.getValue() == state) {
					return s;
				}
			}
			throw new NoSuchElementException(String.valueOf(state));
		}
	}

}


/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit.authority;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import javax.swing.table.AbstractTableModel;

import com.gitblit.client.Translation;
import com.gitblit.utils.X509Utils.RevocationReason;

/**
 * Table model of a list of user certificate models.
 *
 * @author James Moger
 *
 */
public class CertificatesTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	UserCertificateModel ucm;

	enum Columns {
		SerialNumber, Status, Reason, Issued, Expires;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public CertificatesTableModel() {
	}

	@Override
	public int getRowCount() {
		return ucm == null || ucm.certs == null ? 0 : ucm.certs.size();
	}

	@Override
	public int getColumnCount() {
		return Columns.values().length;
	}

	@Override
	public String getColumnName(int column) {
		Columns col = Columns.values()[column];
		switch (col) {
		case SerialNumber:
			return Translation.get("gb.serialNumber");
		case Issued:
			return Translation.get("gb.issued");
		case Expires:
			return Translation.get("gb.expires");
		case Status:
			return Translation.get("gb.status");
		case Reason:
			return Translation.get("gb.reason");
		}
		return "";
	}

	/**
	 * Returns <code>Object.class</code> regardless of <code>columnIndex</code>.
	 *
	 * @param columnIndex
	 *            the column being queried
	 * @return the Object.class
	 */
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Status:
			return CertificateStatus.class;
		case Issued:
			return Date.class;
		case Expires:
			return Date.class;
		case SerialNumber:
			return BigInteger.class;
		default:
			return String.class;
		}
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		default:
			return false;
		}
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		X509Certificate cert = ucm.certs.get(rowIndex);
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Status:
			return ucm.getStatus(cert);
		case SerialNumber:
			return cert.getSerialNumber();
		case Issued:
			return cert.getNotBefore();
		case Expires:
			return cert.getNotAfter();
		case Reason:
			if (ucm.getStatus(cert).equals(CertificateStatus.revoked)) {
				RevocationReason r = ucm.getRevocationReason(cert.getSerialNumber());
				return Translation.get("gb." + r.name());
			}
		}
		return null;
	}

	public X509Certificate get(int modelRow) {
		return ucm.certs.get(modelRow);
	}

	public void setUserCertificateModel(UserCertificateModel ucm) {
		this.ucm = ucm;
		if (ucm == null) {
			return;
		}
		Collections.sort(ucm.certs, new Comparator<X509Certificate>() {
			@Override
			public int compare(X509Certificate o1, X509Certificate o2) {
				// sort by issue date in reverse chronological order
				int result = o2.getNotBefore().compareTo(o1.getNotBefore());
				if (result == 0) {
					// same issue date, show expiring first
					boolean r1 = CertificatesTableModel.this.ucm.isRevoked(o1.getSerialNumber());
					boolean r2 = CertificatesTableModel.this.ucm.isRevoked(o2.getSerialNumber());
					if ((r1 && r2) || (!r1 && !r2)) {
						// both revoked or both not revoked
						// chronlogical order by expiration dates
						result = o1.getNotAfter().compareTo(o2.getNotAfter());
					} else if (r1) {
						// r1 is revoked, r2 first
						return 1;
					} else {
						// r2 is revoked, r1 first
						return -1;
					}
				}
				return result;
			}
		});
	}
}

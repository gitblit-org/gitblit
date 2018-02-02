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
package com.gitblit.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

public class TikaUtils {
    public static String extractText(String ext, String filename, byte[] data) {
	Tika tika = new Tika();
	String fileType = tika.detect(filename);
        try (InputStream is = new ByteArrayInputStream(data)) {
             Logger.getLogger(TikaUtils.class.getName()).info("Tika parsing "+filename);
            return tika.parseToString(is);
        } catch (IOException ex) {
            Logger.getLogger(TikaUtils.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        } catch (TikaException tex) {
            Logger.getLogger(TikaUtils.class.getName()).log(Level.SEVERE, null, tex);
            return "";
        }
    }
}

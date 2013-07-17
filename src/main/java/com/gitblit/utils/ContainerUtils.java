/*
 * Copyright 2012 PD Inc / gitblit.com.
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
package com.gitblit.utils;

import java.io.CharConversionException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.GitBlit;
import com.gitblit.Keys;

/**
 * This is the support class for all container specific code.
 * 
 * @author jpyeron
 */
public class ContainerUtils
{
    private static Logger LOGGER = LoggerFactory.getLogger(ContainerUtils.class);

    /**
     * The support class for managing and evaluating the environment with
     * regards to CVE-2007-0405.
     * 
     * @see http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450
     * @author jpyeron
     */
    public static class CVE_2007_0450
    {
        /**
         * This method will test for know issues in certain containers where %2F
         * is blocked from use in URLs. It will emit a warning to the logger if
         * the configuration of Tomcat causes the URL processing to fail on %2F.
         */
        public static void test()
        {
            if (GitBlit.getBoolean(Keys.web.mountParameters, true)
                    && ((GitBlit.getChar(Keys.web.forwardSlashCharacter, '/')) == '/' || (GitBlit.getChar(
                            Keys.web.forwardSlashCharacter, '/')) == '\\'))
            {
                try
                {
                    if (GitBlit.isGO())
                        ;
                    else if (logCVE_2007_0450Tomcat())
                        ;
                    // else if (logCVE_2007_0450xxx());
                    else
                    {
                        LOGGER.info("Unknown container, cannot check for CVE-2007-0450 aplicability");
                    }
                }
                catch (Throwable t)
                {
                    LOGGER.warn("Failure in checking for CVE-2007-0450 aplicability", t);
                }
            }

        }

        /**
         * This method will test for know issues in certain versions of Tomcat,
         * JBOSS, glassfish, and other embedded uses of Tomcat where %2F is
         * blocked from use in certain URL s. It will emit a warning to the
         * logger if the configuration of Tomcat causes the URL processing to
         * fail on %2F.
         * 
         * @return true if it recognizes Tomcat, false if it does not recognize
         *         Tomcat
         */
        private static boolean logCVE_2007_0450Tomcat()
        {
            try
            {
                byte[] test = "http://server.domain:8080/context/servlet/param%2fparam".getBytes();

                // ByteChunk mb=new ByteChunk();
                Class<?> cByteChunk = Class.forName("org.apache.tomcat.util.buf.ByteChunk");
                Object mb = cByteChunk.newInstance();

                // mb.setBytes(test, 0, test.length);
                Method mByteChunck_setBytes = cByteChunk.getMethod("setBytes", byte[].class, int.class, int.class);
                mByteChunck_setBytes.invoke(mb, test, (int) 0, test.length);

                // UDecoder ud=new UDecoder();
                Class<?> cUDecoder = Class.forName("org.apache.tomcat.util.buf.UDecoder");
                Object ud = cUDecoder.newInstance();

                // ud.convert(mb,false);
                Method mUDecoder_convert = cUDecoder.getMethod("convert", cByteChunk, boolean.class);

                try
                {
                    mUDecoder_convert.invoke(ud, mb, false);
                }
                catch (InvocationTargetException e)
                {
                    if (e.getTargetException() != null && e.getTargetException() instanceof CharConversionException)
                    {
                        LOGGER.warn("You are using a Tomcat-based server and your current settings will prevent grouped repositories, forks, personal repositories, and tree navigation from working properly. Please review the FAQ for details about running Gitblit on Tomcat. http://gitblit.com/faq.html and http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450");
                        return true;
                    }
                    throw e;
                }
            }
            catch (Throwable t)
            {
                // The apache url decoder internals are different, this is not a
                // Tomcat matching the failure pattern for CVE-2007-0450
                if (t instanceof ClassNotFoundException || t instanceof NoSuchMethodException
                        || t instanceof IllegalArgumentException)
                    return false;
                LOGGER.debug("This is a tomcat, but the test operation failed somehow", t);
            }
            return true;
        }
    }

}

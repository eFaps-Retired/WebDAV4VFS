/*
 * Copyright 2003 - 2010 The eFaps Team
 * Copyright 2007 Matthias L. Jugel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Revision:        $Rev$
 * Last Changed:    $Date$
 * Last Changed By: $Author$
 */

package org.efaps.webdav4vfs.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileType;
import org.efaps.webdav4vfs.lock.LockException;
import org.efaps.webdav4vfs.lock.LockManager;
import org.efaps.webdav4vfs.vfs.VFSBackend;


/**
 * @author Matthias L. Jugel
 * @version $Id$
 */
public class PutHandler extends AbstractWebdavHandler {
  private static final Log LOG = LogFactory.getLog(PutHandler.class);

  @Override
public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    FileObject object = VFSBackend.resolveFile(request.getPathInfo());

    try {
      if (!LockManager.getInstance().evaluateCondition(object, getIf(request)).result) {
        response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
        return;
      }
    } catch (LockException e) {
      response.sendError(SC_LOCKED);
      return;
    } catch (ParseException e) {
      response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
      return;
    }
    // it is forbidden to write data on a folder
    if (object.exists() && FileType.FOLDER.equals(object.getType())) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    FileObject parent = object.getParent();
    if (!parent.exists()) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    if (!FileType.FOLDER.equals(parent.getType())) {
      response.sendError(HttpServletResponse.SC_CONFLICT);
      return;
    }

    InputStream is = request.getInputStream();
    OutputStream os = object.getContent().getOutputStream();
    long bytesCopied = IOUtils.copyLarge(is, os);
    String contentLengthHeader = request.getHeader("Content-length");
    LOG.debug(String.format("sent %d/%s bytes", bytesCopied, contentLengthHeader == null ? "unknown" : contentLengthHeader));
    os.flush();
    object.close();

    response.setStatus(HttpServletResponse.SC_CREATED);
  }
}

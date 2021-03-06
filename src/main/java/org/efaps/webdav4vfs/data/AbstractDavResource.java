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

package org.efaps.webdav4vfs.data;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs.FileContent;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.dom4j.Element;
import org.dom4j.Node;

/**
 * This is a DAV resource. This class mainly handles the properties associated
 * with a resource. Please see
 * <a href="http://www.webdav.org/specs/rfc2518.html#dav.properties">RFC2518</a>
 * for information about necessary properties and their allowed values.
 *
 * @author Matthias L. Jugel
 * @version $Id$
 */
public abstract class AbstractDavResource
{
    public static final String STATUS_200 = "HTTP/1.1 200 OK";
    public static final String STATUS_404 = "HTTP/1.1 404 Not Found";
    public static final String STATUS_403 = "HTTP/1.1 403 Forbidden";
    public static final String STATUS_422 = "HTTP/1.1 422 Unprocessable Entity";

    public static final String TAG_ALLPROP = "allprop";
    public static final String TAG_PROPNAMES = "propnames";

    public static final String TAG_PROPSTAT = "propstat";
    public static final String TAG_PROP = "prop";
    public static final String TAG_STATUS = "status";
    public static final String TAG_PROP_SET = "set";
    public static final String TAG_PROP_REMOVE = "remove";

    public static final String PROP_CREATION_DATE = "creationdate";
    public static final String PROP_DISPLAY_NAME = "displayname";
    public static final String PROP_GET_CONTENT_LANGUAGE = "getcontentlanguage";
    public static final String PROP_GET_CONTENT_LENGTH = "getcontentlength";
    public static final String PROP_GET_CONTENT_TYPE = "getcontenttype";
    public static final String PROP_GET_ETAG = "getetag";
    public static final String PROP_GET_LAST_MODIFIED = "getlastmodified";
    public static final String PROP_LOCK_DISCOVERY = "lockdiscovery";
    public static final String PROP_RESOURCETYPE = "resourcetype";
    public static final String PROP_SOURCE = "source";
    public static final String PROP_SUPPORTED_LOCK = "supportedlock";

    // non-standard properties
    public static final String PROP_QUOTA = "quota";
    public static final String PROP_QUOTA_USED = "quotaused";
    public static final String PROP_QUOTA_AVAILABLE_BYTES = "quota-available-bytes";
    public static final String PROP_QUOTA_USED_BYTES = "quota-used-bytes";

    // list of standard supported properties (for allprop/propname)
    public static final List<String> ALL_PROPERTIES = Arrays.asList(
          PROP_CREATION_DATE,
          PROP_DISPLAY_NAME,
          PROP_GET_CONTENT_LANGUAGE,
          PROP_GET_CONTENT_LENGTH,
          PROP_GET_CONTENT_TYPE,
          PROP_GET_ETAG,
          PROP_GET_LAST_MODIFIED,
          PROP_LOCK_DISCOVERY,
          PROP_RESOURCETYPE,
          PROP_SOURCE,
          PROP_SUPPORTED_LOCK
    );

    protected final FileObject object;

    public AbstractDavResource(final FileObject object)
    {
        this.object = object;
    }

    /**
     * Set or remove a properties. This method expects a list of xml elements
     * that are the properties to be set or removed. These elements must not be
     * detached from their original &lt;set&gt; or &lt;remove&gt; parent tags
     * to be able to determine what should be done with the property.
     *
     * @param _root                 root of the result document
     * @param _requestedProperties  list of properties to work on
     * @return returns the root of the result document
     */
    public Element setPropertyValues(final Element _root,
                                     final List<Element> _requestedProperties)
    {
        // initialize the <propstat> element for 200
        final Element okPropStatEl = _root.addElement(TAG_PROPSTAT);
        final Element okPropEl = okPropStatEl.addElement(TAG_PROP);

        // initialize the <propstat> element for 422
        final Element failPropStatEl = _root.addElement(TAG_PROPSTAT);
        final Element failPropEl = failPropStatEl.addElement(TAG_PROP);

        // go through the properties and try to set/remove them,
        // if it fails, add to the failed list
        for (final Node propertyEl : _requestedProperties) {
            if (!setPropertyValue(okPropEl, (Element) propertyEl)) {
                failPropEl.addElement(((Element) propertyEl).getQName());
            }
        }

        // only add the OK section, if there is content
        if (okPropEl.elements().size() > 0) {
            okPropStatEl.addElement(TAG_STATUS).addText(STATUS_200);
        } else {
            okPropStatEl.detach();
        }

        // only add the failed section, if there is content
        if (failPropEl.elements().size() > 0) {
            failPropStatEl.addElement(TAG_STATUS).addText(STATUS_422);
        } else {
            failPropStatEl.detach();
        }

        return _root;
    }

    /**
     * Get property values. This method expects one of either &lt;allprop&gt;,
     * &lt;propnames&gt; or &lt;prop&gt;. If the element is &lt;prop&gt; it
     * will go through the list of it's children and request the values. For
     * &lt;allprop&gt; it will get values of all known properties and
     * &lt;propnames&gt; will return the names of all known properties.
     *
     * @param root       the root of the result document
     * @param propertyEl the prop, propname or allprop element
     * @return the root of the result document
     */
    public Element getPropertyValues(final Element root,
                                     final Element propertyEl)
    {
        // initialize the <propstat> for 200
        final Element okPropStatEl = root.addElement(TAG_PROPSTAT);
        final Element okPropEl = okPropStatEl.addElement(TAG_PROP);

        // initialize the <propstat> element for 404
        final Element failPropStatEl = root.addElement(TAG_PROPSTAT);
        final Element failPropEl = failPropStatEl.addElement(TAG_PROP);

        if (TAG_ALLPROP.equalsIgnoreCase(propertyEl.getName()) ||
            TAG_PROPNAMES.equalsIgnoreCase(propertyEl.getName())) {

            boolean ignoreValue = TAG_PROPNAMES.equalsIgnoreCase(propertyEl.getName());

            // get all known standard properties
            for (String propName : ALL_PROPERTIES) {
                if (!getPropertyValue(okPropEl, propName, ignoreValue)) {
                    failPropEl.addElement(propName);
                }
            }

            // additionally try to add all the custom properties
            try {
                final FileContent objectContent = object.getContent();
                for (String attributeName : objectContent.getAttributeNames()) {
                    if (!getPropertyValue(okPropEl, attributeName, ignoreValue)) {
                        failPropEl.addElement(attributeName);
                    }
                }
            } catch (final FileSystemException e) {
                LogFactory.getLog(getClass()).error(String.format("can't read attribute properties from '%s'",
                                                              object.getName()), e);
            }
            } else {
                final List<?> requestedProperties = propertyEl.elements();
                for (Object propertyElObject : requestedProperties) {
                    final Element propEl = (Element) propertyElObject;
                    final String nameSpace = propEl.getNamespaceURI();
                    if (!getPropertyValue(okPropEl, getFQName(nameSpace, propEl.getName()), false)) {
                        failPropEl.addElement(propEl.getQName());
                    }
                }
            }

        // only add the OK section, if there is content
        if (okPropEl.elements().size() > 0) {
            okPropStatEl.addElement(TAG_STATUS).addText(STATUS_200);
        } else {
            okPropStatEl.detach();
        }

        // only add the failed section, if there is content
        if (failPropEl.elements().size() > 0) {
            failPropStatEl.addElement(TAG_STATUS).addText(STATUS_404);
        } else {
            failPropStatEl.detach();
        }

        return root;
    }

    /**
     * Return a specially encoded full qualified name for a name space and
     * name. As HTTP headers may only contain ASCII, the name space is base64
     * encoded if it exists.
     *
     * @param _nameSpace    name space
     * @param _name         name of the property
     * @return the encoded attribute name
     */
    protected String getFQName(final String _nameSpace,
                               final String _name)
    {
        String prefix = "";
        if (!"DAV:".equals(_nameSpace) && null != _nameSpace && !"".equals(_nameSpace)) {
          prefix = new String(Base64.encodeBase64(_nameSpace.getBytes()));
        }
        return String.format("%s%s", prefix, _name);
    }

    /**
     * Set the property and its value. Returns false if the property cannot be
     * processed.
     *
     * @param _root         response stat element
     * @param _propertyEl   property element to set
     * @return false if this property cannot be set
     */
    protected abstract boolean setPropertyValue(final Element _root,
                                                final Element _propertyEl);

    /**
     * Get the property value and append it to the xml document (root). If this
     * method returns false, the property does not exist.
     *
     * @param _root         root element to add the property name (and
     *                      possible value to)
     * @param _propertyName the property name to read
     * @param _ignoreValue  ignore the value and just add the name
     * @return whether the property exists
     */
    protected abstract boolean getPropertyValue(final Element _root,
                                                final String _propertyName,
                                                final boolean _ignoreValue);
}

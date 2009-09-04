package org.apache.tapestry5.sax.internal;

import org.apache.tapestry5.ioc.Location;
import org.apache.tapestry5.ioc.Messages;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.internal.util.MessagesImpl;

class SaxParserMessages
{
    private static final Messages MESSAGES = MessagesImpl.forClass(SaxParserMessages.class);

    static String contentInsideBodyNotAllowed(Location location)
    {
        return MESSAGES.format("content-inside-body-not-allowed", location);
    }

    static String mixinsInvalidWithoutIdOrType(String elementName)
    {
        return MESSAGES.format("mixins-invalid-without-id-or-type", elementName);
    }

    static String invalidId(String messageKey, String idValue)
    {
        return MESSAGES.format(messageKey, idValue);
    }

    static String newParserError(Resource resource, Throwable cause)
    {
        return MESSAGES.format("new-parser-error", resource, cause);
    }

    static String undefinedTapestryAttribute(String elementName, String attributeName, String allowedAttributeName)
    {
        return MESSAGES.format("undefined-tapestry-attribute", elementName, attributeName, allowedAttributeName);
    }

    static String parameterElementNameRequired()
    {
        return MESSAGES.get("parameter-element-name-required");
    }

    static String parameterElementDoesNotAllowAttributes()
    {
        return MESSAGES.get("parameter-element-does-not-allow-attributes");
    }

    static String invalidPathForLibraryNamespace(String URI)
    {
        return MESSAGES.format("invalid-path-for-library-namespace", URI);
    }

}

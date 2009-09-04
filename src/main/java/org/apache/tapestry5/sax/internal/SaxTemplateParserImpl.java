package org.apache.tapestry5.sax.internal;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.internal.parser.AttributeToken;
import org.apache.tapestry5.internal.parser.BlockToken;
import org.apache.tapestry5.internal.parser.BodyToken;
import org.apache.tapestry5.internal.parser.CDATAToken;
import org.apache.tapestry5.internal.parser.CommentToken;
import org.apache.tapestry5.internal.parser.ComponentTemplate;
import org.apache.tapestry5.internal.parser.ComponentTemplateImpl;
import org.apache.tapestry5.internal.parser.DTDToken;
import org.apache.tapestry5.internal.parser.DefineNamespacePrefixToken;
import org.apache.tapestry5.internal.parser.EndElementToken;
import org.apache.tapestry5.internal.parser.ExpansionToken;
import org.apache.tapestry5.internal.parser.ExtensionPointToken;
import org.apache.tapestry5.internal.parser.ParameterToken;
import org.apache.tapestry5.internal.parser.StartComponentToken;
import org.apache.tapestry5.internal.parser.StartElementToken;
import org.apache.tapestry5.internal.parser.TemplateToken;
import org.apache.tapestry5.internal.parser.TextToken;
import org.apache.tapestry5.internal.parser.TokenType;
import org.apache.tapestry5.internal.services.TemplateParser;
import org.apache.tapestry5.ioc.Location;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.ioc.internal.util.LocationImpl;
import org.apache.tapestry5.ioc.internal.util.TapestryException;
import org.apache.tapestry5.ioc.util.Stack;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class SaxTemplateParserImpl implements TemplateParser
{
    private static final String MIXINS_ATTRIBUTE_NAME = "mixins";

    private static final String TYPE_ATTRIBUTE_NAME = "type";

    private static final String ID_ATTRIBUTE_NAME = "id";

    public static final String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";

    /**
     * Used as the namespace URI for Tapestry templates.
     */
    public static final String TAPESTRY_SCHEMA_5_0_0 = "http://tapestry.apache.org/schema/tapestry_5_0_0.xsd";

    /**
     * Adds several new elements.
     */
    public static final String TAPESTRY_SCHEMA_5_1_0 = "http://tapestry.apache.org/schema/tapestry_5_1_0.xsd";

    // Might want to change this from a Set to a map from URI to version number (if we hit a 3rd
    // version of the namespace URI).
    private static final Set<String> TAPESTRY_SCHEMA_URIS = CollectionFactory.newSet(
            TAPESTRY_SCHEMA_5_0_0,
            TAPESTRY_SCHEMA_5_1_0);

    /**
     * Special namespace used to denote Block parameters to components, as a (preferred) alternative to the t:parameter
     * element.  The simple element name is the name of the parameter.
     */
    private static final String TAPESTRY_PARAMETERS_URI = "tapestry:parameter";

    /**
     * URI prefix used to identify a Tapestry library, the remainder of the URI becomes a prefix on the element name.
     */
    private static final String LIB_NAMESPACE_URI_PREFIX = "tapestry-library:";

    /**
     * Pattern used to parse the path portion of the library namespace URI.  A series of simple identifiers with slashes
     * allowed as seperators.
     */
    private static final Pattern LIBRARY_PATH_PATTERN = Pattern.compile("^[a-z]\\w*(/[a-z]\\w*)*$",
                                                                        Pattern.CASE_INSENSITIVE);

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z]\\w*$", Pattern.CASE_INSENSITIVE);

    /**
     * Any amount of mixed simple whitespace (space, tab, form feed) mixed with at least one carriage return or line
     * feed, followed by any amount of whitespace.  Will be reduced to a single linefeed.
     */
    private static final Pattern REDUCE_LINEBREAKS_PATTERN = Pattern.compile("[ \\t\\f]*[\\r\\n]\\s*",
                                                                             Pattern.MULTILINE);

    /**
     * Used when compressing whitespace, matches any sequence of simple whitespace (space, tab, formfeed). Applied after
     * REDUCE_LINEBREAKS_PATTERN.
     */
    private static final Pattern REDUCE_WHITESPACE_PATTERN = Pattern.compile("[ \\t\\f]+", Pattern.MULTILINE);

    // Note the use of the non-greedy modifier; this prevents the pattern from merging multiple
    // expansions on the same text line into a single large
    // but invalid expansion.

    private static final Pattern EXPANSION_PATTERN = Pattern.compile("\\$\\{\\s*(.*?)\\s*}");

    private final Map<String, URL> configuration;

    private final boolean defaultCompressWhitespace;

    public SaxTemplateParserImpl(Map<String, URL> configuration,
            @Symbol(SymbolConstants.COMPRESS_WHITESPACE) boolean defaultCompressWhitespace)
    {
        this.configuration = configuration;
        this.defaultCompressWhitespace = defaultCompressWhitespace;
    }

    public ComponentTemplate parseTemplate(Resource templateResource)
    {
        XMLReader xmlReader;
        TemplateContentHandler handler = new TemplateContentHandler(templateResource, defaultCompressWhitespace);
        try
        {
            xmlReader = XMLReaderFactory.createXMLReader();
            xmlReader.setContentHandler(handler);
            xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            xmlReader.setEntityResolver(new TemplateEntityResolver(configuration));
            xmlReader.parse(new InputSource(templateResource.openStream()));
            return new ComponentTemplateImpl(templateResource, handler.getTokens(), handler.getComponentIds(), handler.hasExtension(), handler.getOverrides());
        }
        catch (Exception ex)
        {
            throw new TapestryException(SaxParserMessages.newParserError(templateResource, ex), handler.getLocation(), ex);
        }
    }

    private static class TemplateEntityResolver implements EntityResolver
    {
        private final Map<String, URL> configuration;

        public TemplateEntityResolver(Map<String, URL> configuration)
        {
            this.configuration = configuration;
        }

        public InputSource resolveEntity(String publicId, String systemId) throws SAXException,
                IOException
        {
            URL url = configuration.get(publicId);

            try
            {
                if (url != null)
                    return new InputSource(url.openStream());
            }
            catch (IOException ex)
            {
                throw new SAXException(
                        String.format("Unable to open stream for resource %s: %s",
                                      url,
                                      InternalUtils.toMessage(ex)), ex);
            }

            return null;
        }
    }

    private static class TemplateContentHandler implements ContentHandler, LexicalHandler
    {
        private final Resource resource;

        private boolean compressWhitespace;

        /**
         * A stack to remember the last values of compressWhitespace.
         */
        private final Stack<Boolean> compressWhitespaceStack = CollectionFactory.newStack();

        /**
         * All template tokens.
         */
        private List<TemplateToken> tokens = CollectionFactory.newList();

        /**
         * Temporarily saved list of tokens (when inside override - this is the outside list)
         */
        private List<TemplateToken> savedTokens;

        /**
         * Map from override id to a list of tokens; this actually works both for overrides defined by this template and
         * overrides provided by this template.
         */
        private Map<String, List<TemplateToken>> overrides;

        /**
         * Has this template an extend block.
         */
        private boolean extension;

        /**
         * List which saved temporarily accumulated {@link DefineNamespacePrefixToken}s.
         */
        private final List<DefineNamespacePrefixToken> namespacePrefixToken = CollectionFactory.newList();

        /**
         * Primarily used as a set of componentIds (to check for duplicates and conflicts).
         */
        private final Map<String, Location> componentIds = CollectionFactory.newCaseInsensitiveMap();

        private Locator locator;

        /**
         * Inside a t:remove element.
         */
        private boolean inRemove = false;

        /**
         * Inside a t:body element.
         */
        private boolean inBody = false;

        /**
         * Inside a t:replace element.
         */
        private boolean inReplace = false;

        /**
         * A stack to remember the last values of insideComponent.
         */
        private final Stack<Boolean> insideComponentStack = CollectionFactory.newStack();

        private static enum ContentState
        {
            OUTSIDE_CONTENT,
            IN_CONTENT,
            AFTER_CONTENT
        };

        private ContentState contentState = ContentState.OUTSIDE_CONTENT;

        private Location textStartLocation;

        private Location cachedLocation;

        private final StringBuilder textBuffer = new StringBuilder();

        public TemplateContentHandler(Resource resource, boolean compressWhitespace)
        {
            this.resource = resource;
            this.compressWhitespace = compressWhitespace;
        }

        public Map<String, Location> getComponentIds()
        {
            return componentIds;
        }

        public List<TemplateToken> getTokens()
        {
            return tokens;
        }

        public Map<String, List<TemplateToken>> getOverrides()
        {
            return overrides;
        }

        public boolean hasExtension()
        {
            return extension;
        }

        public void characters(char[] ch, int start, int length) throws SAXException
        {
            if (inRemove || contentState == ContentState.AFTER_CONTENT) return;

            if(inBody)
                throw new IllegalStateException(SaxParserMessages.contentInsideBodyNotAllowed(getLocation()));

            if (textStartLocation == null)
                textStartLocation = getLocation();

            textBuffer.append(ch, start, length);
        }

        public void endDocument() throws SAXException
        {
            // ignore
        }

        public void endPrefixMapping(String prefix) throws SAXException
        {
            // ignore
        }

        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException
        {
            // ignore
        }

        public void processingInstruction(String target, String data) throws SAXException
        {
            // ignore
        }

        public void setDocumentLocator(Locator locator)
        {
            this.locator = locator;
        }

        public void skippedEntity(String name) throws SAXException
        {
            // ignore
        }

        public void startDocument() throws SAXException
        {
            // ignore
        }

        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException
        {
            if (inRemove || contentState == ContentState.AFTER_CONTENT) return;

            if(inBody)
                throw new IllegalStateException(SaxParserMessages.contentInsideBodyNotAllowed(getLocation()));

            if(extension && !inReplace && !"replace".equalsIgnoreCase(localName))
                throw new RuntimeException("Child element of <extend> must be <replace>.");

            processTextBuffer();

            checkForXMLSpaceAttribute(atts);

            if(TAPESTRY_SCHEMA_URIS.contains(uri))
            {
                if (TAPESTRY_SCHEMA_5_1_0.equals(uri))
                {
                    if ("remove".equalsIgnoreCase(localName))
                    {
                        inRemove = true;
                        return;
                    }
                    else if ("content".equalsIgnoreCase(localName))
                    {
                        if (contentState == ContentState.IN_CONTENT)
                            throw new IllegalStateException(
                                    "The <content> element may not be nested within another <content> element.");
                        contentState = ContentState.IN_CONTENT;
                        tokens.clear();
                        return;
                    }
                    else if ("extension-point".equalsIgnoreCase(localName))
                    {
                        // An extension point adds a token that represents where the override (either the default
                        // provided in the parent template, or the true override from a child template) is positioned.

                        String id = getRequiredIdAttribute(localName, atts);

                        if(savedTokens != null)
                            throw new IllegalStateException("The <extension-point> element may not be nested within another <extension-point> element.");

                        tokens.add(new ExtensionPointToken(id, getLocation()));

                        savedTokens = tokens;
                        tokens = CollectionFactory.newList();
                        saveTokensToOverrides(id);

                        return;
                    }
                    else if ("extend".equalsIgnoreCase(localName))
                    {
                        mustBeRoot(localName);

                        extension = true;

                        return;
                    }
                    else if ("replace".equalsIgnoreCase(localName))
                    {
                        if(!extension || tokens.size() > 0)
                            throw new RuntimeException("The <replace> element may only appear directly within an extend element.");

                        String id = getRequiredIdAttribute(localName, atts);

                        saveTokensToOverrides(id);

                        inReplace = true;

                        return;
                    }
                }

                if("body".equalsIgnoreCase(localName))
                {
                    tokens.add(new BodyToken(getLocation()));
                    inBody = true;
                    return;
                }
                else if("block".equalsIgnoreCase(localName))
                {
                    String blockId = getSingleParameter(localName, atts, "id");

                    validateId(blockId, "invalid-block-id");

                    tokens.add(new BlockToken(blockId ,getLocation()));

                    insideComponentStack.push(false);

                    return;
                }
                else if("parameter".equalsIgnoreCase(localName))
                {
                    String name = getSingleParameter(localName, atts, "name");

                    if (InternalUtils.isBlank(name))
                        throw new TapestryException(SaxParserMessages.parameterElementNameRequired(), getLocation(), null);

                    ensureParameterWithinComponent();

                    tokens.add(new ParameterToken(name, getLocation()));

                    insideComponentStack.push(false);

                    return;
                }
                else if("container".equalsIgnoreCase(localName))
                {
                    mustBeRoot(localName);

                    return; // do nothing
                }
                else
                {
                    possibleTapestryComponent(atts, null, uri, localName.replace('.', '/'));
                    return;
                }
            }
            else if (TAPESTRY_PARAMETERS_URI.equals(uri))
            {
                ensureParameterWithinComponent();

                if (atts.getLength() > 0)
                    throw new TapestryException(SaxParserMessages.parameterElementDoesNotAllowAttributes(), getLocation(),
                                                null);

                tokens.add(new ParameterToken(localName, getLocation()));

                insideComponentStack.push(false);

                return;
            }
            else if (uri != null && uri.startsWith(LIB_NAMESPACE_URI_PREFIX))
            {
                String path = uri.substring(LIB_NAMESPACE_URI_PREFIX.length());

                if (!LIBRARY_PATH_PATTERN.matcher(path).matches())
                    throw new RuntimeException(SaxParserMessages.invalidPathForLibraryNamespace(uri));

                possibleTapestryComponent(atts, null, uri, path + "/" + localName);
                return;
            }

            // Just an ordinary element ... unless it has t:id or t:type

            possibleTapestryComponent(atts, localName, uri, null);
        }

        private void saveTokensToOverrides(String id)
        {
            if (overrides == null)
                overrides = CollectionFactory.newCaseInsensitiveMap();

            overrides.put(id, tokens);
        }

        private void mustBeRoot(String name)
        {
            if(tokens.size() > 0)
            {
                for(TemplateToken token : tokens)
                {
                    if(token.getTokenType() != TokenType.DTD)
                        throw new RuntimeException(
                                String.format("Element <%s> is only valid as the root element of a template.", name));
                }
            }
        }

        private void possibleTapestryComponent(Attributes atts, String elementName, String elementNamespaceUri, String identifiedType)
        {
            String id = null;
            String type = identifiedType;
            String mixins = null;

            int count = atts.getLength();

            Location location = getLocation();

            List<TemplateToken> attributeTokens = CollectionFactory.newList();
            
            for (int i = 0; i < count; i++)
            {
                String localName = atts.getLocalName(i);

                if (InternalUtils.isBlank(localName)) continue;

                String uri = atts.getURI(i);

                if (isXMLSpaceAttribute(uri, localName)) continue;

                String value = atts.getValue(i);

                if (TAPESTRY_SCHEMA_URIS.contains(uri))
                {
                    if (localName.equalsIgnoreCase(ID_ATTRIBUTE_NAME))
                    {
                        id = nullForBlank(value);

                        validateId(id, "invalid-component-id");

                        continue;
                    }

                    if (type == null && localName.equalsIgnoreCase(TYPE_ATTRIBUTE_NAME))
                    {
                        type = nullForBlank(value);
                        continue;
                    }

                    if (localName.equalsIgnoreCase(MIXINS_ATTRIBUTE_NAME))
                    {
                        mixins = nullForBlank(value);
                        continue;
                    }

                    // Anything else is the name of a Tapestry component parameter that is simply
                    // not part of the template's doctype for the element being instrumented.
                }

                attributeTokens.add(new AttributeToken(uri, localName, value, location));
            }

            boolean isComponent = (id != null || type != null);

            // If provided t:mixins but not t:id or t:type, then its not quite a component

            if (mixins != null && !isComponent)
                throw new TapestryException(SaxParserMessages.mixinsInvalidWithoutIdOrType(elementName), location, null);

            if (isComponent)
            {
                tokens.add(new StartComponentToken(elementName, id, type, mixins, location));
            }
            else
            {
                tokens.add(new StartElementToken(elementNamespaceUri, elementName, location));
            }

            tokens.addAll(namespacePrefixToken);
            namespacePrefixToken.clear();

            tokens.addAll(attributeTokens);

            if (id != null)
                componentIds.put(id, location);

            insideComponentStack.push(isComponent);
        }

        private void checkForXMLSpaceAttribute(Attributes atts)
        {
            for (int i = 0; i < atts.getLength(); i++)
            {
                if (isXMLSpaceAttribute(atts.getURI(i), atts.getLocalName(i)))
                {
                    compressWhitespace = !"preserve".equals(atts.getValue(i));
                    break;
                }
            }
            compressWhitespaceStack.push(compressWhitespace);
        }

        public void endElement(String uri, String localName, String qName) throws SAXException
        {
            if(contentState == ContentState.AFTER_CONTENT)
                return;

            processTextBuffer();

            if(!inRemove)
                compressWhitespace = compressWhitespaceStack.pop();

            if (TAPESTRY_SCHEMA_URIS.contains(uri))
            {
                if (TAPESTRY_SCHEMA_5_1_0.equals(uri))
                {
                    if ("remove".equalsIgnoreCase(localName))
                    {
                        inRemove = false;
                        return;
                    }
                    else if ("content".equalsIgnoreCase(localName))
                    {
                        contentState = ContentState.AFTER_CONTENT;
                        return;
                    }
                    else if ("extension-point".equalsIgnoreCase(localName))
                    {
                        tokens = savedTokens;
                        savedTokens = null;
                        return;
                    }
                    else if ("extend".equalsIgnoreCase(localName))
                    {
                        return;
                    }
                    else if ("replace".equalsIgnoreCase(localName))
                    {
                        tokens = CollectionFactory.newList();
                        inReplace = false;
                        return;
                    }
                }
                if("body".equalsIgnoreCase(localName))
                {
                    inBody = false;
                    return;
                }
                else if ("container".equalsIgnoreCase(localName))
                    return;
            }

            if(inRemove) return;

            tokens.add(new EndElementToken(getLocation()));

            insideComponentStack.pop();
        }

        private void ensureParameterWithinComponent()
        {
            if(insideComponentStack.isEmpty() || !insideComponentStack.peek())
                throw new RuntimeException("Block parameters are only allowed directly within component elements.");
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException
        {
            if (InternalUtils.isBlank(uri)) return;

            if (TAPESTRY_SCHEMA_URIS.contains(uri)) return;

            if (uri.equals(TAPESTRY_PARAMETERS_URI)) return;

            if (uri.startsWith(LIB_NAMESPACE_URI_PREFIX)) return;

            namespacePrefixToken.add(new DefineNamespacePrefixToken(uri, prefix, getLocation()));
        }

        private String nullForBlank(String input)
        {
            return InternalUtils.isBlank(input) ? null : input;
        }

        private String getSingleParameter(String localName, Attributes atts, String attributeName)
        {
            String result = null;

            for(int i=0; i < atts.getLength(); i++)
            {
                String name = atts.getLocalName(i);
                if(isXMLSpaceAttribute(atts.getURI(i), name))
                    continue;

                if(attributeName.equalsIgnoreCase(name))
                    result = atts.getValue(i);
                else
                    throw new TapestryException(SaxParserMessages.undefinedTapestryAttribute(localName,
                            name, attributeName), getLocation(), null);
            }
            return result;
        }

        private String getRequiredIdAttribute(String localName, Attributes atts)
        {
            String id = getSingleParameter(localName, atts, "id");

            if (InternalUtils.isBlank(id))
                throw new RuntimeException(String.format(
                        "The <%s> element must have an id attribute.",
                        localName));

            return id;
        }

        private void validateId(String id, String messageKey)
        {
            if (id == null) return;

            if (ID_PATTERN.matcher(id).matches()) return;

            // Not a match.

            throw new TapestryException(SaxParserMessages.invalidId(messageKey, id), getLocation(), null);
        }

        private boolean isXMLSpaceAttribute(String uri, String localName)
        {
            return XML_NAMESPACE_URI.equals(uri) &&
                    "space".equals(localName);
        }

        Location getLocation()
        {
            int lineNumber = locator == null ? -1 : locator.getLineNumber();

            if (cachedLocation != null && cachedLocation.getLine() != lineNumber)
                cachedLocation = null;

            if (cachedLocation == null)
                cachedLocation = new LocationImpl(resource, lineNumber);

            return cachedLocation;
        }

        /**
         * Processes the accumulated text in the text buffer as a text token.
         */
        private void processTextBuffer()
        {
            if (textBuffer.length() != 0)
                convertTextBufferToTokens();

             textStartLocation = null;
        }

        private void convertTextBufferToTokens()
        {
            String text = textBuffer.toString();

            textBuffer.setLength(0);

            if (compressWhitespace)
            {
                text = compressWhitespaceInText(text);

                if (InternalUtils.isBlank(text)) return;
            }

            addTokensForText(text);
        }

        /**
         * Reduces vertical whitespace to a single newline, then reduces horizontal whitespace to a single space.
         *
         * @param text
         * @return compressed version of text
         */
        private String compressWhitespaceInText(String text)
        {
            String linebreaksReduced = REDUCE_LINEBREAKS_PATTERN.matcher(text).replaceAll("\n");

            return REDUCE_WHITESPACE_PATTERN.matcher(linebreaksReduced).replaceAll(" ");
        }

        /**
         * Scans the text, using a regular expression pattern, for expansion patterns, and adds appropriate tokens for what
         * it finds.
         *
         * @param text to add as {@link org.apache.tapestry5.internal.parser.TextToken}s and {@link
         *             org.apache.tapestry5.internal.parser.ExpansionToken}s
         */
        private void addTokensForText(String text)
        {
            Matcher matcher = EXPANSION_PATTERN.matcher(text);

            int startx = 0;

            // The big problem with all this code is that everything gets assigned to the
            // start of the text block, even if there are line breaks leading up to it.
            // That's going to take a lot more work and there are bigger fish to fry.  In addition,
            // TAPESTRY-2028 means that the whitespace has likely been stripped out of the text
            // already anyway.

            while (matcher.find())
            {
                int matchStart = matcher.start();

                if (matchStart != startx)
                {
                    String prefix = text.substring(startx, matchStart);

                    tokens.add(new TextToken(prefix, textStartLocation));
                }

                // Group 1 includes the real text of the expansion, with whitespace around the
                // expression (but inside the curly braces) excluded.

                String expression = matcher.group(1);

                tokens.add(new ExpansionToken(expression, textStartLocation));

                startx = matcher.end();
            }

            // Catch anything after the final regexp match.

            if (startx < text.length())
                tokens.add(new TextToken(text.substring(startx, text.length()), textStartLocation));
        }

        public void comment(char[] ch, int start, int length) throws SAXException
        {
            if (inRemove || contentState == ContentState.AFTER_CONTENT) return;

            processTextBuffer();

            tokens.add(new CommentToken(new String(ch, start, length).trim(), getLocation()));
        }

        public void startCDATA() throws SAXException
        {
            if (inRemove || contentState == ContentState.AFTER_CONTENT) return;

            processTextBuffer();

            textStartLocation = getLocation();
        }

        public void endCDATA() throws SAXException
        {
            if (inRemove || contentState == ContentState.AFTER_CONTENT) return;

            tokens.add(new CDATAToken(textBuffer.toString(), textStartLocation));
            textBuffer.setLength(0);
            textStartLocation = null;
        }

        public void startDTD(String name, String publicId, String systemId) throws SAXException
        {
            tokens.add(new DTDToken(name, publicId, systemId, getLocation()));
            inRemove = true;
        }

        public void endDTD() throws SAXException
        {
            inRemove = false;
        }

        public void startEntity(String name) throws SAXException
        {
            // ignore
        }

        public void endEntity(String name) throws SAXException
        {
            // ignore
        }
    }

}
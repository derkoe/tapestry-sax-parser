package org.apache.tapestry5.sax.internal;

import java.net.URL;

import org.apache.tapestry5.internal.services.TemplateParser;
import org.apache.tapestry5.internal.services.UpdateListenerHubImpl;
import org.apache.tapestry5.ioc.MappedConfiguration;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.annotations.Local;

public class SaxParserModule
{
    public static void bind(ServiceBinder binder)
    {
        binder.bind(TemplateParser.class, SaxTemplateParserImpl.class).withId(
                "TemplateParserOverride");
    }

    public static void contributeTemplateParserOverride(MappedConfiguration<String, URL> config)
    {
        // Any class inside the internal module would do. Or we could move all these
        // files to o.a.t.services.

        Class<UpdateListenerHubImpl> c = UpdateListenerHubImpl.class;

        config.add("-//W3C//DTD XHTML 1.0 Strict//EN", c.getResource("xhtml1-strict.dtd"));
        config.add("-//W3C//DTD XHTML 1.0 Transitional//EN", c
                .getResource("xhtml1-transitional.dtd"));
        config.add("-//W3C//DTD XHTML 1.0 Frameset//EN", c.getResource("xhtml1-frameset.dtd"));
        config.add("-//W3C//DTD HTML 4.01//EN", c.getResource("xhtml1-strict.dtd"));
        config.add("-//W3C//DTD HTML 4.01 Transitional//EN", c
                .getResource("xhtml1-transitional.dtd"));
        config.add("-//W3C//DTD HTML 4.01 Frameset//EN", c.getResource("xhtml1-frameset.dtd"));
        config.add("-//W3C//ENTITIES Latin 1 for XHTML//EN", c.getResource("xhtml-lat1.ent"));
        config.add("-//W3C//ENTITIES Symbols for XHTML//EN", c.getResource("xhtml-symbol.ent"));
        config.add("-//W3C//ENTITIES Special for XHTML//EN", c.getResource("xhtml-special.ent"));
    }

    public static void contributeServiceOverride(MappedConfiguration<Class, Object> configuration,
            @Local TemplateParser override)
    {
        configuration.add(TemplateParser.class, override);
    }
}

/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package org.lealone.opscenter.web.thymeleaf;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.common.WebEnvironment;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;

/**
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="http://matty.io">Matty Southall</a>
 */
// 改编自io.vertx.ext.web.templ.thymeleaf.impl.ThymeleafTemplateEngineImpl
// 主要修改了ResourceTemplateResolver，允许指定templateRoot，默认字符集也改成了UTF-8
public class ThymeleafTemplateEngineImpl implements ThymeleafTemplateEngine {

    private final TemplateEngine templateEngine = new TemplateEngine();
    private final ResourceTemplateResolver templateResolver;

    public ThymeleafTemplateEngineImpl(Vertx vertx, String templateRoot) {
        ResourceTemplateResolver templateResolver = new ResourceTemplateResolver(vertx, templateRoot);
        templateResolver.setCacheable(!WebEnvironment.development());
        templateResolver.setTemplateMode(ThymeleafTemplateEngine.DEFAULT_TEMPLATE_MODE);

        this.templateResolver = templateResolver;
        this.templateEngine.setTemplateResolver(templateResolver);
        // There's no servlet context in Vert.x, so we override default link builder
        // See https://github.com/vert-x3/vertx-web/issues/161
        this.templateEngine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(final IExpressionContext context, final String base,
                    final Map<String, Object> parameters) {
                return "/";
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap() {
        return (T) templateEngine;
    }

    @Override
    public ThymeleafTemplateEngine setMode(TemplateMode mode) {
        templateResolver.setTemplateMode(mode);
        return this;
    }

    @Override
    public TemplateEngine getThymeleafTemplateEngine() {
        return this.templateEngine;
    }

    @Override
    public void render(Map<String, Object> context, String templateFile, Handler<AsyncResult<Buffer>> handler) {
        Buffer buffer = Buffer.buffer();

        try {
            synchronized (this) {
                templateEngine.process(templateFile, new WebIContext(context, (String) context.get("lang")),
                        new Writer() {
                            @Override
                            public void write(char[] cbuf, int off, int len) {
                                buffer.appendString(new String(cbuf, off, len));
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public void close() {
                            }
                        });
            }

            handler.handle(Future.succeededFuture(buffer));
        } catch (Exception ex) {
            handler.handle(Future.failedFuture(ex));
        }
    }

    private static class WebIContext implements IContext {
        private final Map<String, Object> data;
        private final Locale locale;

        private WebIContext(Map<String, Object> data, String lang) {
            this.data = data;
            if (lang == null) {
                this.locale = Locale.getDefault();
            } else {
                this.locale = Locale.forLanguageTag(lang);
            }
        }

        @Override
        public java.util.Locale getLocale() {
            return locale;
        }

        @Override
        public boolean containsVariable(String name) {
            return data.containsKey(name);
        }

        @Override
        public Set<String> getVariableNames() {
            return data.keySet();
        }

        @Override
        public Object getVariable(String name) {
            return data.get(name);
        }
    }

    private static class ResourceTemplateResolver extends StringTemplateResolver {
        private final Vertx vertx;
        private final String templateRoot;

        ResourceTemplateResolver(Vertx vertx, String templateRoot) {
            super();
            this.vertx = vertx;
            try {
                this.templateRoot = new File(templateRoot).getCanonicalPath();
            } catch (IOException e) {
                throw new RuntimeException("Invalid templateRoot: " + templateRoot);
            }
            setName("vertx/Thymeleaf3");
        }

        @Override
        protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate,
                String template, Map<String, Object> templateResolutionAttributes) {
            String templatePath;
            try {
                // 模板文件的位置总是在templateRoot之下
                templatePath = new File(template).getCanonicalPath();
                if (!templatePath.startsWith(templateRoot)) {
                    templatePath = new File(templateRoot, template).getCanonicalPath();
                    if (!templatePath.startsWith(templateRoot)) {
                        templatePath = null;
                    }
                }
            } catch (IOException e) {
                templatePath = null;
            }
            if (templatePath == null)
                throw new RuntimeException("Invalid template file: " + template + ", template root: " + templateRoot);
            return new StringTemplateResource(vertx.fileSystem().readFileBlocking(templatePath).toString("UTF-8"));
        }
    }
}

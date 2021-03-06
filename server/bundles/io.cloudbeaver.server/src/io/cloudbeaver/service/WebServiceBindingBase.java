/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2020 DBeaver Corp and others
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
package io.cloudbeaver.service;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.cloudbeaver.DBWUtils;
import io.cloudbeaver.DBWebException;
import io.cloudbeaver.DBWService;
import io.cloudbeaver.model.WebConnectionInfo;
import io.cloudbeaver.model.session.WebSession;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Web service implementation
 */
public abstract class WebServiceBindingBase<API_TYPE extends DBWService> implements DBWServiceBindingGraphQL {

    private final Class<API_TYPE> apiInterface;
    private final API_TYPE serviceImpl;
    private final String schemaFileName;

    public WebServiceBindingBase(Class<API_TYPE> apiInterface, API_TYPE impl, String schemaFileName) {
        this.apiInterface = apiInterface;
        this.serviceImpl = impl;
        this.schemaFileName = schemaFileName;
    }

    protected API_TYPE getServiceImpl() {
        return serviceImpl;
    }

    @Override
    public TypeDefinitionRegistry getTypeDefinition() throws DBWebException {
        return loadSchemaDefinition(getClass(), schemaFileName);
    }

    /**
     * Creates proxy for permission checks and other general API calls validation/logging.
     */
    protected  API_TYPE getService(DataFetchingEnvironment env) {
        Object proxyImpl = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{apiInterface}, new ServiceInvocationHandler(serviceImpl));
        return apiInterface.cast(proxyImpl);
    }

    public static TypeDefinitionRegistry loadSchemaDefinition(Class theClass, String schemaPath) throws DBWebException {
        try (InputStream schemaStream = theClass.getClassLoader().getResourceAsStream(schemaPath)) {
            if (schemaStream == null) {
                throw new IOException("Schema file '" + schemaPath + "' not found");
            }
            try (Reader schemaReader = new InputStreamReader(schemaStream)) {
                return new SchemaParser().parse(schemaReader);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading core schema", e);
        }
    }

    protected static HttpServletRequest getServletRequest(DataFetchingEnvironment env) {
        return DBWUtils.getServletRequest(env);
    }

    protected static WebSession getWebSession(DBWBindingContext model, DataFetchingEnvironment env) throws DBWebException {
        return model.getSessionManager().getWebSession(getServletRequest(env));
    }

    protected static WebConnectionInfo getWebConnection(DBWBindingContext model, DataFetchingEnvironment env) throws DBWebException {
        return getWebSession(model, env).getWebConnectionInfo(env.getArgument("connectionId"));
    }

    private class ServiceInvocationHandler implements InvocationHandler {
        private final API_TYPE impl;

        ServiceInvocationHandler(API_TYPE impl) {
            this.impl = impl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return method.invoke(impl, args);
        }
    }
}

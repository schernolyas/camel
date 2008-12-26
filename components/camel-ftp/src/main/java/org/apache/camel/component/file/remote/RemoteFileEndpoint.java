/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.remote;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Remote file endpoint.
 */
public class RemoteFileEndpoint extends ScheduledPollEndpoint {
    private static final transient Log LOG = LogFactory.getLog(RemoteFileEndpoint.class);
    private static final transient String DEFAULT_STRATEGYFACTORY_CLASS =
            "org.apache.camel.component.file.remote.strategy.RemoteFileProcessStrategyFactory";

    private RemoteFileProcessStrategy remoteFileProcessStrategy;
    private RemoteFileOperations remoteFileOperations;
    private RemoteFileConfiguration configuration;
    private boolean noop;
    private String tempPrefix;
    private String moveNamePrefix;
    private String moveNamePostfix;
    private String preMoveNamePrefix;
    private String preMoveNamePostfix;
    private String excludedNamePrefix;
    private String excludedNamePostfix;
    private boolean recursive;
    private String regexPattern;
    private boolean setNames = true;
    private boolean delete;
    private Expression expression;
    private Expression preMoveExpression;
    private RemoteFileFilter filter;
    private Comparator<RemoteFile> sorter;
    private Comparator<RemoteFileExchange> sortBy;
    private RemoteFileExclusiveReadLockStrategy exclusiveReadLockStrategy;
    private String readLock = "none";
    private long readLockTimeout;

    public RemoteFileEndpoint(String uri, RemoteFileComponent component, RemoteFileOperations remoteFileOperations, RemoteFileConfiguration configuration) {
        super(uri, component);
        this.remoteFileOperations = remoteFileOperations;
        this.configuration = configuration;
    }

    public RemoteFileExchange createExchange() {
        return new RemoteFileExchange(getCamelContext(), getExchangePattern(), null);
    }

    public RemoteFileExchange createExchange(RemoteFile remote) {
        return new RemoteFileExchange(getCamelContext(), getExchangePattern(), remote);
    }

    public RemoteFileProducer createProducer() throws Exception {
        return new RemoteFileProducer(this, remoteFileOperations);
    }

    public RemoteFileConsumer createConsumer(Processor processor) throws Exception {
        String protocol = getConfiguration().getProtocol();
        ObjectHelper.notEmpty(protocol, "protocol");

        RemoteFileConsumer consumer = null;
        if ("ftp".equals(protocol)) {
            consumer = new FtpConsumer(this, processor, remoteFileOperations);
        } else if ("sftp".equals(protocol)) {
            consumer = new SftpConsumer(this, processor, remoteFileOperations);
        } else {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }

        if (isDelete() && (getMoveNamePrefix() != null || getMoveNamePostfix() != null || getExpression() != null)) {
            throw new IllegalArgumentException("You cannot set delete=true and a moveNamePrefix, moveNamePostfix or expression option");
        }

        configureConsumer(consumer);
        return consumer;
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Return the file name that will be auto-generated for the given message if none is provided
     */
    public String getGeneratedFileName(Message message) {
        return getFileFriendlyMessageId(message.getMessageId());
    }

    protected String getFileFriendlyMessageId(String id) {
        return UuidGenerator.generateSanitizedId(id);
    }

    public RemoteFileProcessStrategy getRemoteFileProcessStrategy() {
        if (remoteFileProcessStrategy == null) {
            remoteFileProcessStrategy = createRemoteFileStrategy();
            LOG.debug("Using remote file process strategy: " + remoteFileProcessStrategy);
        }
        return remoteFileProcessStrategy;
    }

    public void setRemoteFileProcessStrategy(RemoteFileProcessStrategy remoteFileProcessStrategy) {
        this.remoteFileProcessStrategy = remoteFileProcessStrategy;
    }

    public boolean isNoop() {
        return noop;
    }

    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public String getMoveNamePrefix() {
        return moveNamePrefix;
    }

    public void setMoveNamePrefix(String moveNamePrefix) {
        this.moveNamePrefix = moveNamePrefix;
    }

    public String getMoveNamePostfix() {
        return moveNamePostfix;
    }

    public void setMoveNamePostfix(String moveNamePostfix) {
        this.moveNamePostfix = moveNamePostfix;
    }

    public String getPreMoveNamePrefix() {
        return preMoveNamePrefix;
    }

    public void setPreMoveNamePrefix(String preMoveNamePrefix) {
        this.preMoveNamePrefix = preMoveNamePrefix;
    }

    public String getPreMoveNamePostfix() {
        return preMoveNamePostfix;
    }

    public void setPreMoveNamePostfix(String preMoveNamePostfix) {
        this.preMoveNamePostfix = preMoveNamePostfix;
    }

    public String getExcludedNamePrefix() {
        return excludedNamePrefix;
    }

    public void setExcludedNamePrefix(String excludedNamePrefix) {
        this.excludedNamePrefix = excludedNamePrefix;
    }

    public String getExcludedNamePostfix() {
        return excludedNamePostfix;
    }

    public void setExcludedNamePostfix(String excludedNamePostfix) {
        this.excludedNamePostfix = excludedNamePostfix;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public boolean isSetNames() {
        return setNames;
    }

    public void setSetNames(boolean setNames) {
        this.setNames = setNames;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    /**
     * Sets the expression based on {@link org.apache.camel.language.simple.FileLanguage}
     */
    public void setExpression(String fileLanguageExpression) {
        this.expression = FileLanguage.file(fileLanguageExpression);
    }

    public Expression getPreMoveExpression() {
        return preMoveExpression;
    }

    public void setPreMoveExpression(Expression preMoveExpression) {
        this.preMoveExpression = preMoveExpression;
    }

    /**
     * Sets the pre move expression based on {@link org.apache.camel.language.simple.FileLanguage}
     */
    public void setPreMoveExpression(String fileLanguageExpression) {
        this.preMoveExpression = FileLanguage.file(fileLanguageExpression);
    }

    public RemoteFileFilter getFilter() {
        return filter;
    }

    public void setFilter(RemoteFileFilter filter) {
        this.filter = filter;
    }

    public Comparator<RemoteFile> getSorter() {
        return sorter;
    }

    public void setSorter(Comparator<RemoteFile> sorter) {
        this.sorter = sorter;
    }

    public Comparator<RemoteFileExchange> getSortBy() {
        return sortBy;
    }

    public void setSortBy(Comparator<RemoteFileExchange> sortBy) {
        this.sortBy = sortBy;
    }

    public void setSortBy(String expression) {
        setSortBy(expression, false);
    }

    public void setSortBy(String expression, boolean reverse) {
        setSortBy(DefaultRemoteFileSorter.sortByFileLanguage(expression, reverse));
    }

    public String getTempPrefix() {
        return tempPrefix;
    }

    /**
     * Enables and uses temporary prefix when writing files, after write it will be renamed to the correct name.
     */
    public void setTempPrefix(String tempPrefix) {
        this.tempPrefix = tempPrefix;
    }

    public RemoteFileConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(RemoteFileConfiguration configuration) {
        this.configuration = configuration;
    }

    public RemoteFileExclusiveReadLockStrategy getExclusiveReadLockStrategy() {
        return exclusiveReadLockStrategy;
    }

    public void setExclusiveReadLockStrategy(RemoteFileExclusiveReadLockStrategy exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }

    public String getReadLock() {
        return readLock;
    }

    public void setReadLock(String readLock) {
        this.readLock = readLock;
    }

    public long getReadLockTimeout() {
        return readLockTimeout;
    }

    public void setReadLockTimeout(long readLockTimeout) {
        this.readLockTimeout = readLockTimeout;
    }

    /**
     * Should the file be moved after consuming?
     */
    public boolean isMoveFile() {
        return moveNamePostfix != null || moveNamePrefix != null || preMoveNamePostfix != null || preMoveNamePrefix != null || expression != null;
    }

    /**
     * Returns human readable server information for logging purpose
     */
    public String remoteServerInformation() {
        return configuration.remoteServerInformation();
    }

    /**
     * Configures the given message with the file which sets the body to the file object
     * and sets the {@link FileComponent#HEADER_FILE_NAME} header.
     */
    public void configureMessage(RemoteFile file, Message message) {
        message.setBody(file);
        message.setHeader(FileComponent.HEADER_FILE_NAME, file.getRelativeFileName());
    }

    /**
     * A strategy method to lazily create the file strategy
     */
    protected RemoteFileProcessStrategy createRemoteFileStrategy() {
        Class<?> factory = null;
        try {
            FactoryFinder finder = new FactoryFinder("META-INF/services/org/apache/camel/component/");
            factory = finder.findClass("ftp", "strategy.factory.");
        } catch (ClassNotFoundException e) {
            LOG.debug("'strategy.factory.class' not found", e);
        } catch (IOException e) {
            LOG.debug("No strategy factory defined in 'META-INF/services/org/apache/camel/component/'", e);
        }

        if (factory == null) {
            // use default
            factory = ObjectHelper.loadClass(DEFAULT_STRATEGYFACTORY_CLASS);
            if (factory == null) {
                throw new TypeNotPresentException("RemoteFileProcessStrategyFactory class not found", null);
            }
        }

        try {
            Method factoryMethod = factory.getMethod("createRemoteFileProcessStrategy", Map.class);
            return (RemoteFileProcessStrategy) ObjectHelper.invokeMethod(factoryMethod, null, getParamsAsMap());
        } catch (NoSuchMethodException e) {
            throw new TypeNotPresentException(factory.getSimpleName()
                    + ".createRemoteFileProcessStrategy(RemoteFileEndpoint endpoint) method not found", e);
        }
    }

    protected Map<String, Object> getParamsAsMap() {
        Map<String, Object> params = new HashMap<String, Object>();

        if (isNoop()) {
            params.put("noop", Boolean.toString(true));
        }
        if (isDelete()) {
            params.put("delete", Boolean.toString(true));
        }
        if (moveNamePrefix != null) {
            params.put("moveNamePrefix", moveNamePrefix);
        }
        if (moveNamePostfix != null) {
            params.put("moveNamePostfix", moveNamePostfix);
        }
        if (preMoveNamePrefix != null) {
            params.put("preMoveNamePrefix", preMoveNamePrefix);
        }
        if (preMoveNamePostfix != null) {
            params.put("preMoveNamePostfix", preMoveNamePostfix);
        }
        if (expression != null) {
            params.put("expression", expression);
        }
        if (preMoveExpression != null) {
            params.put("preMoveExpression", preMoveExpression);
        }
        if (exclusiveReadLockStrategy != null) {
            params.put("exclusiveReadLockStrategy", exclusiveReadLockStrategy);
        }
        if (readLock != null) {
            params.put("readLock", readLock);
        }
        if (readLockTimeout > 0) {
            params.put("readLockTimeout", Long.valueOf(readLockTimeout));
        }
        return params;
    }

}

/*
*Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/
package org.wso2.esb.integration.common.utils;

import org.apache.axiom.attachments.ByteArrayDataSource;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.engine.context.beans.ContextUrls;
import org.wso2.carbon.automation.engine.context.beans.Tenant;
import org.wso2.carbon.automation.engine.context.beans.User;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.integration.common.admin.client.SecurityAdminServiceClient;
import org.wso2.carbon.integration.common.utils.LoginLogoutClient;
import org.wso2.carbon.security.mgt.stub.config.SecurityAdminServiceSecurityConfigExceptionException;
import org.wso2.carbon.sequences.stub.types.SequenceEditorException;
import org.wso2.esb.integration.common.utils.clients.stockquoteclient.StockQuoteClient;
import org.xml.sax.SAXException;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

public abstract class ESBIntegrationTest {
    protected Log log = LogFactory.getLog(getClass());
    protected StockQuoteClient axis2Client;
    protected ContextUrls contextUrls = new ContextUrls();
    protected String sessionCookie;
    protected OMElement synapseConfiguration = null;
    protected ESBTestCaseUtils esbUtils;
    private List<String> proxyServicesList = null;
    private List<String> sequencesList = null;
    private List<String> endpointsList = null;
    private List<String> localEntryList = null;
    private List<String> messageProcessorsList = null;
    private List<String> messageStoresList = null;
    private List<String> sequenceTemplateList = null;
    private List<String> apiList = null;
    private List<String> priorityExecutorList = null;
    private List<String[]> scheduledTaskList = null;
    protected AutomationContext context;
    protected Tenant tenantInfo;
    protected User userInfo;

    protected TestUserMode userMode;

    protected void init() throws Exception {
        userMode = TestUserMode.SUPER_TENANT_ADMIN;
        init(userMode);

    }

    protected void init(TestUserMode userMode) throws Exception {
        axis2Client = new StockQuoteClient();
        context = new AutomationContext("ESB", userMode);
        contextUrls = context.getContextUrls();
        sessionCookie = login(context);
        esbUtils = new ESBTestCaseUtils();
        tenantInfo = context.getContextTenant();
        userInfo = tenantInfo.getContextUser();
    }

    protected void cleanup() throws Exception {
        try {
            if (synapseConfiguration != null) {
                esbUtils.deleteArtifact(synapseConfiguration, contextUrls.getBackEndUrl(), sessionCookie);
                if (context.getProductGroup().isClusterEnabled()) {

                    long deploymentDelay = Long.parseLong(context.getConfigurationValue("//deploymentDelay"));
                    Thread.sleep(deploymentDelay);
                    Iterator<OMElement> proxies = synapseConfiguration.getChildrenWithLocalName("proxy");
                    while (proxies.hasNext()) {
                        String proxy = proxies.next().getAttributeValue(new QName("name"));

                        Assert.assertTrue(isProxyWSDlNotExist(getProxyServiceURLHttp(proxy), deploymentDelay)
                                , "UnDeployment Synchronizing failed in workers");
                        Assert.assertTrue(isProxyWSDlNotExist(getProxyServiceURLHttp(proxy), deploymentDelay)
                                , "UnDeployment Synchronizing failed in workers");
                        Assert.assertTrue(isProxyWSDlNotExist(getProxyServiceURLHttp(proxy), deploymentDelay)
                                , "UnDeployment Synchronizing failed in workers");
                    }
                }
            }

            deleteProxyServices();

            deleteSequences();

            deleteEndpoints();

            deleteMessageProcessors();

            deleteMessageStores();

            deleteSequenceTemplates();

            deleteLocalEntries();

            deleteApi();

            deletePriorityExecutors();

            deleteScheduledTasks();

        } finally {
            synapseConfiguration = null;
            proxyServicesList = null;
            messageProcessorsList = null;
            proxyServicesList = null;
            sequencesList = null;
            endpointsList = null;
            localEntryList = null;
            apiList = null;
            priorityExecutorList = null;
            axis2Client.destroy();
            axis2Client = null;
            esbUtils = null;
            scheduledTaskList = null;

        }
    }

    protected String getMainSequenceURL() {
        String mainSequenceUrl = contextUrls.getServiceUrl();
        if (mainSequenceUrl.endsWith("/services")) {
            mainSequenceUrl = mainSequenceUrl.replace("/services", "");
        }
        if(!mainSequenceUrl.endsWith("/")){
           mainSequenceUrl = mainSequenceUrl + "/";
        }
        return mainSequenceUrl;

    }

    protected String getProxyServiceURLHttp(String proxyServiceName) {
        return contextUrls.getServiceUrl() + "/" + proxyServiceName;
    }

    protected String getApiInvocationURL(String apiName) {
        return getMainSequenceURL() + apiName;
    }

    protected String getProxyServiceURLHttps(String proxyServiceName) {
        return contextUrls.getSecureServiceUrl() + "/" + proxyServiceName;
    }

    protected void loadSampleESBConfiguration(int sampleNo) throws Exception {
        OMElement synapseSample = esbUtils.loadESBSampleConfiguration(sampleNo);
        updateESBConfiguration(synapseSample);

    }

    protected void loadESBConfigurationFromClasspath(String relativeFilePath) throws Exception {
        relativeFilePath = relativeFilePath.replaceAll("[\\\\/]", Matcher.quoteReplacement(File.separator));

        OMElement synapseConfig = esbUtils.loadResource(relativeFilePath);
        updateESBConfiguration(synapseConfig);

    }

    protected void updateESBConfiguration(OMElement synapseConfig) throws Exception {

        if (synapseConfiguration == null) {
            synapseConfiguration = synapseConfig;
        } else {
            Iterator<OMElement> itr = synapseConfig.cloneOMElement().getChildElements();
            while (itr.hasNext()) {
                synapseConfiguration.addChild(itr.next());
            }
        }
        esbUtils.updateESBConfiguration(setEndpoints(synapseConfig), contextUrls.getBackEndUrl(), sessionCookie);

        if (context.getProductGroup().isClusterEnabled()) {
            long deploymentDelay = Long.parseLong(context.getConfigurationValue("//deploymentDelay"));
            Thread.sleep(deploymentDelay);
            Iterator<OMElement> proxies = synapseConfig.getChildrenWithLocalName("proxy");
            while (proxies.hasNext()) {
                String proxy = proxies.next().getAttributeValue(new QName("name"));

                Assert.assertTrue(isProxyWSDlExist(getProxyServiceURLHttp(proxy), deploymentDelay)
                        , "Deployment Synchronizing failed in workers");
                Assert.assertTrue(isProxyWSDlExist(getProxyServiceURLHttp(proxy), deploymentDelay)
                        , "Deployment Synchronizing failed in workers");
                Assert.assertTrue(isProxyWSDlExist(getProxyServiceURLHttp(proxy), deploymentDelay)
                        , "Deployment Synchronizing failed in workers");
            }
        }
    }

    protected void addProxyService(OMElement proxyConfig) throws Exception {
        String proxyName = proxyConfig.getAttributeValue(new QName("name"));
        if (esbUtils.isProxyServiceExist(contextUrls.getBackEndUrl(), sessionCookie, proxyName)) {
            esbUtils.deleteProxyService(contextUrls.getBackEndUrl(), sessionCookie, proxyName);
        }
        if (proxyServicesList == null) {
            proxyServicesList = new ArrayList<String>();
        }
        proxyServicesList.add(proxyName);
        esbUtils.addProxyService(contextUrls.getBackEndUrl(), sessionCookie, setEndpoints(proxyConfig));

       /* if (ExecutionEnvironment.stratos.name().equalsIgnoreCase(getExecutionEnvironment())) {
            long deploymentDelay = FrameworkFactory.getFrameworkProperties(
                    ProductConstant.ESB_SERVER_NAME).getEnvironmentVariables().getDeploymentDelay();

            Assert.assertTrue(isProxyWSDlExist(getProxyServiceURL(proxyName), deploymentDelay)
                    , "Deployment Synchronizing failed in workers");
            Assert.assertTrue(isProxyWSDlExist(getProxyServiceURL(proxyName), deploymentDelay)
                    , "Deployment Synchronizing failed in workers");
            Assert.assertTrue(isProxyWSDlExist(getProxyServiceURL(proxyName), deploymentDelay)
                    , "Deployment Synchronizing failed in workers");

        }*/
    }

    protected void isProxyDeployed(String proxyServiceName) throws Exception {
        Assert.assertTrue(esbUtils.isProxyDeployed(contextUrls.getBackEndUrl(), sessionCookie,
                                                   proxyServiceName), "Proxy Deployment failed or time out");
    }

    protected void deleteProxyService(String proxyServiceName) throws Exception {
        if (esbUtils.isProxyServiceExist(contextUrls.getBackEndUrl(), sessionCookie, proxyServiceName)) {
            esbUtils.deleteProxyService(contextUrls.getBackEndUrl(), sessionCookie, proxyServiceName);
            Assert.assertTrue(esbUtils.isProxyUnDeployed(contextUrls.getBackEndUrl(), sessionCookie,
                                                         proxyServiceName), "Proxy Deletion failed or time out");
        }
        if (proxyServicesList != null && proxyServicesList.contains(proxyServiceName)) {
            proxyServicesList.remove(proxyServiceName);
        }
    }

    protected void deleteSequence(String sequenceName)
            throws SequenceEditorException, RemoteException {
        if (esbUtils.isSequenceExist(contextUrls.getBackEndUrl(), sessionCookie, sequenceName)) {
            esbUtils.deleteSequence(contextUrls.getBackEndUrl(), sessionCookie, sequenceName);
        }
        if (sequencesList != null && sequencesList.contains(sequenceName)) {
            sequencesList.remove(sequenceName);
        }
    }

    protected void addSequence(OMElement sequenceConfig) throws Exception {
        String sequenceName = sequenceConfig.getAttributeValue(new QName("name"));
        if (esbUtils.isSequenceExist(contextUrls.getBackEndUrl(), sessionCookie, sequenceName)) {
            esbUtils.deleteSequence(contextUrls.getBackEndUrl(), sessionCookie, sequenceName);
        }
        esbUtils.addSequence(contextUrls.getBackEndUrl(), sessionCookie, setEndpoints(sequenceConfig));
        if (sequencesList == null) {
            sequencesList = new ArrayList<String>();
        }
        sequencesList.add(sequenceName);
    }

    protected void addEndpoint(OMElement endpointConfig)
            throws Exception {
        String endpointName = endpointConfig.getAttributeValue(new QName("name"));
        if (esbUtils.isSequenceExist(contextUrls.getBackEndUrl(), sessionCookie, endpointName)) {
            esbUtils.deleteEndpoint(contextUrls.getBackEndUrl(), sessionCookie, endpointName);
        }
        esbUtils.addEndpoint(contextUrls.getBackEndUrl(), sessionCookie, setEndpoints(endpointConfig));
        if (endpointsList == null) {
            endpointsList = new ArrayList<String>();
        }
        endpointsList.add(endpointName);

    }

    protected void addLocalEntry(OMElement localEntryConfig) throws Exception {
        String localEntryName = localEntryConfig.getAttributeValue(new QName("key"));
        if (esbUtils.isLocalEntryExist(contextUrls.getBackEndUrl(), sessionCookie, localEntryName)) {
            esbUtils.deleteLocalEntry(contextUrls.getBackEndUrl(), sessionCookie, localEntryName);
        }
        esbUtils.addLocalEntry(contextUrls.getBackEndUrl(), sessionCookie, localEntryConfig);

        if (localEntryList == null) {
            localEntryList = new ArrayList<String>();
        }
        localEntryList.add(localEntryName);
    }

    protected void addMessageProcessor(OMElement messageProcessorConfig) throws Exception {
        String messageProcessorName = messageProcessorConfig.getAttributeValue(new QName("name"));
        if (esbUtils.isMessageProcessorExist(contextUrls.getBackEndUrl(), sessionCookie, messageProcessorName)) {
            esbUtils.deleteMessageProcessor(contextUrls.getBackEndUrl(), sessionCookie, messageProcessorName);
        }
        esbUtils.addMessageProcessor(contextUrls.getBackEndUrl(), sessionCookie, setEndpoints(messageProcessorConfig));
        if (messageProcessorsList == null) {
            messageProcessorsList = new ArrayList<String>();
        }
        messageProcessorsList.add(messageProcessorName);
    }

    protected void addMessageStore(OMElement messageStoreConfig) throws Exception {
        String messageStoreName = messageStoreConfig.getAttributeValue(new QName("name"));
        if (esbUtils.isMessageStoreExist(contextUrls.getBackEndUrl(), sessionCookie, messageStoreName)) {
            esbUtils.deleteMessageStore(contextUrls.getBackEndUrl(), sessionCookie, messageStoreName);
        }
        esbUtils.addMessageStore(contextUrls.getBackEndUrl(), sessionCookie, setEndpoints(messageStoreConfig));
        if (messageStoresList == null) {
            messageStoresList = new ArrayList<String>();
        }
        messageStoresList.add(messageStoreName);
    }

    protected void addSequenceTemplate(OMElement sequenceTemplate) throws Exception {
        String name = sequenceTemplate.getAttributeValue(new QName("name"));
        if (esbUtils.isSequenceTemplateExist(contextUrls.getBackEndUrl(), sessionCookie, name)) {
            esbUtils.deleteSequenceTemplate(contextUrls.getBackEndUrl(), sessionCookie, name);
        }
        esbUtils.addSequenceTemplate(contextUrls.getBackEndUrl(), sessionCookie, setEndpoints(sequenceTemplate));

        if (sequenceTemplateList == null) {
            sequenceTemplateList = new ArrayList<String>();
        }
        sequenceTemplateList.add(name);
    }

    protected void addApi(OMElement api) throws Exception {
        String apiName = api.getAttributeValue(new QName("name"));
        if (esbUtils.isApiExist(contextUrls.getBackEndUrl(), sessionCookie, apiName)) {
            esbUtils.deleteApi(contextUrls.getBackEndUrl(), sessionCookie, apiName);
        }
        esbUtils.addAPI(contextUrls.getBackEndUrl(), sessionCookie, api);

        if (apiList == null) {
            apiList = new ArrayList<String>();
        }
        apiList.add(apiName);
    }

    protected void addPriorityExecutor(OMElement priorityExecutor) throws Exception {
        String executorName = priorityExecutor.getAttributeValue(new QName("name"));
        if (esbUtils.isPriorityExecutorExist(contextUrls.getBackEndUrl(), sessionCookie, executorName)) {
            esbUtils.deletePriorityExecutor(contextUrls.getBackEndUrl(), sessionCookie, executorName);
        }
        esbUtils.addPriorityExecutor(contextUrls.getBackEndUrl(), sessionCookie, priorityExecutor);

        if (priorityExecutorList == null) {
            priorityExecutorList = new ArrayList<String>();
        }
        priorityExecutorList.add(executorName);
    }

    protected void addScheduledTask(OMElement task) throws Exception {
        String taskName = task.getAttributeValue(new QName("name"));
        String taskGroup = task.getAttributeValue(new QName("group"));
        if (esbUtils.isScheduleTaskExist(contextUrls.getBackEndUrl(), sessionCookie, taskName)) {
            esbUtils.deleteScheduleTask(contextUrls.getBackEndUrl(), sessionCookie, taskName, taskGroup);
        }
        esbUtils.addScheduleTask(contextUrls.getBackEndUrl(), sessionCookie, task);

        if (scheduledTaskList == null) {
            scheduledTaskList = new ArrayList<String[]>();
        }
        scheduledTaskList.add(new String[]{taskName, taskGroup});
    }

    protected void applySecurity(String serviceName, int policyId, String[] userGroups)
            throws SecurityAdminServiceSecurityConfigExceptionException, RemoteException,
                   InterruptedException {
        SecurityAdminServiceClient securityAdminServiceClient = new SecurityAdminServiceClient(contextUrls.getBackEndUrl(), sessionCookie);
        //  if (FrameworkFactory.getFrameworkProperties(ProductConstant.ESB_SERVER_NAME).getEnvironmentSettings().is_runningOnStratos()) {

        //      securityAdminServiceClient.applySecurity(serviceName, policyId + "", userGroups,
        //    new String[]{"service.jks"}, "service.jks");
        //  } else {
        securityAdminServiceClient.applySecurity(serviceName, policyId + "", userGroups,
                                                 new String[]{"wso2carbon.jks"}, "wso2carbon.jks");
        //  }
        log.info("Security Scenario " + policyId + " Applied");

        Thread.sleep(1000);

    }

    private void deleteMessageProcessors() {
        if (messageProcessorsList != null) {
            Iterator<String> itr = messageProcessorsList.iterator();
            while (itr.hasNext()) {
                String messageProcessor = itr.next();
                try {
                    if (esbUtils.isMessageProcessorExist(contextUrls.getBackEndUrl(), sessionCookie, messageProcessor)) {
                        esbUtils.deleteMessageProcessor(contextUrls.getBackEndUrl(), sessionCookie, messageProcessor);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying Message Processor. " + e.getMessage());
                }
            }
            messageProcessorsList.clear();
        }
    }

    private void deleteMessageStores() {
        if (messageStoresList != null) {
            Iterator<String> itr = messageStoresList.iterator();
            while (itr.hasNext()) {
                String messageStore = itr.next();
                try {
                    if (esbUtils.isMessageStoreExist(contextUrls.getBackEndUrl(), sessionCookie, messageStore)) {
                        esbUtils.deleteMessageStore(contextUrls.getBackEndUrl(), sessionCookie, messageStore);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying Message store. " + e.getMessage());
                }
            }
            messageStoresList.clear();
        }
    }

    private void deleteSequences() {
        if (sequencesList != null) {
            Iterator<String> itr = sequencesList.iterator();
            while (itr.hasNext()) {
                String sequence = itr.next();
                if (!sequence.equalsIgnoreCase("fault")) {
                    try {
                        if (esbUtils.isSequenceExist(contextUrls.getBackEndUrl(), sessionCookie, sequence)) {
                            esbUtils.deleteSequence(contextUrls.getBackEndUrl(), sessionCookie, sequence);
                        }
                    } catch (Exception e) {
                        Assert.fail("while undeploying Sequence. " + e.getMessage());
                    }
                }
            }
            sequencesList.clear();
        }
    }

    private void deleteProxyServices() {
        if (proxyServicesList != null) {
            Iterator<String> itr = proxyServicesList.iterator();
            while (itr.hasNext()) {
                String proxyName = itr.next();
                try {
                    if (esbUtils.isProxyServiceExist(contextUrls.getBackEndUrl(), sessionCookie, proxyName)) {
                        esbUtils.deleteProxyService(contextUrls.getBackEndUrl(), sessionCookie, proxyName);

                   /*     if (ExecutionEnvironment.stratos.name().equalsIgnoreCase(getExecutionEnvironment())) {
                            long deploymentDelay = FrameworkFactory.getFrameworkProperties(
                                    ProductConstant.ESB_SERVER_NAME).getEnvironmentVariables().getDeploymentDelay();

                            Assert.assertTrue(isProxyWSDlNotExist(getProxyServiceURL(proxyName), deploymentDelay)
                                    , "Proxy UnDeployment Synchronizing failed in workers");
                            Assert.assertTrue(isProxyWSDlNotExist(getProxyServiceURL(proxyName), deploymentDelay)
                                    , "Proxy UnDeployment Synchronizing failed in workers");
                            Assert.assertTrue(isProxyWSDlNotExist(getProxyServiceURL(proxyName), deploymentDelay)
                                    , "Proxy UnDeployment Synchronizing failed in workers");

                        }*/
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying Proxy. " + e.getMessage());
                }
            }
            proxyServicesList.clear();
        }
    }

    private void deleteEndpoints() {
        if (endpointsList != null) {
            Iterator<String> itr = endpointsList.iterator();
            while (itr.hasNext()) {
                String endpoint = itr.next();
                try {
                    if (esbUtils.isEndpointExist(contextUrls.getBackEndUrl(), sessionCookie, endpoint)) {
                        esbUtils.deleteEndpoint(contextUrls.getBackEndUrl(), sessionCookie, endpoint);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying Endpoint. " + e.getMessage());
                }
            }
            endpointsList.clear();
        }
    }

    private void deleteLocalEntries() {
        if (localEntryList != null) {
            Iterator<String> itr = localEntryList.iterator();
            while (itr.hasNext()) {
                String localEntry = itr.next();
                try {
                    if (esbUtils.isLocalEntryExist(contextUrls.getBackEndUrl(), sessionCookie, localEntry)) {
                        esbUtils.deleteLocalEntry(contextUrls.getBackEndUrl(), sessionCookie, localEntry);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying LocalEntry. " + e.getMessage());
                }
            }
            localEntryList.clear();
        }
    }

    private void deleteSequenceTemplates() {
        if (sequenceTemplateList != null) {
            Iterator<String> itr = sequenceTemplateList.iterator();
            while (itr.hasNext()) {
                String localEntry = itr.next();
                try {
                    if (esbUtils.isSequenceTemplateExist(contextUrls.getBackEndUrl(), sessionCookie, localEntry)) {
                        esbUtils.deleteSequenceTemplate(contextUrls.getBackEndUrl(), sessionCookie, localEntry);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying Sequence Template. " + e.getMessage());
                }
            }
            sequenceTemplateList.clear();
        }
    }

    private void deleteApi() {
        if (apiList != null) {
            Iterator<String> itr = apiList.iterator();
            while (itr.hasNext()) {
                String api = itr.next();
                try {
                    if (esbUtils.isApiExist(contextUrls.getBackEndUrl(), sessionCookie, api)) {
                        esbUtils.deleteApi(contextUrls.getBackEndUrl(), sessionCookie, api);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying Api. " + e.getMessage());
                }
            }
            apiList.clear();
        }
    }

    private void deletePriorityExecutors() {
        if (priorityExecutorList != null) {
            Iterator<String> itr = priorityExecutorList.iterator();
            while (itr.hasNext()) {
                String executor = itr.next();
                try {
                    if (esbUtils.isPriorityExecutorExist(contextUrls.getBackEndUrl(), sessionCookie, executor)) {
                        esbUtils.deleteProxyService(contextUrls.getBackEndUrl(), sessionCookie, executor);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying Priority Executor. " + e.getMessage());
                }
            }
            priorityExecutorList.clear();
        }
    }

    private void deleteScheduledTasks() {
        if (scheduledTaskList != null) {
            Iterator<String[]> itr = scheduledTaskList.iterator();
            while (itr.hasNext()) {
                String[] executor = itr.next();
                try {
                    if (esbUtils.isScheduleTaskExist(contextUrls.getBackEndUrl(), sessionCookie, executor[0])) {
                        esbUtils.deleteScheduleTask(contextUrls.getBackEndUrl(), sessionCookie, executor[0], executor[1]);
                    }
                } catch (Exception e) {
                    Assert.fail("while undeploying ScheduledTask Executor. " + e.getMessage());
                }
            }
            scheduledTaskList.clear();
        }
    }

    protected boolean isRunningOnStratos() throws XPathExpressionException {
        return context.getConfigurationValue("//executionEnvironment").equals("platform");
    }

    protected String getESBResourceLocation() {
        return FrameworkPathUtil.getSystemResourceLocation() + File.separator + "artifacts" + File.separator + "ESB";
    }

    protected String getBackEndServiceUrl(String serviceName) throws XPathExpressionException {
        return EndpointGenerator.getBackEndServiceEndpointUrl(serviceName);
    }


    protected OMElement setEndpoints(OMElement synapseConfig)
            throws XMLStreamException, XPathExpressionException {
        if (isBuilderEnabled()) {
            return synapseConfig;
        }
        String config = replaceEndpoints(synapseConfig.toString());
        return AXIOMUtil.stringToOM(config);
    }

    protected DataHandler setEndpoints(DataHandler dataHandler)
            throws XMLStreamException, IOException, XPathExpressionException {
        if (isBuilderEnabled()) {
            return dataHandler;
        }
        String config = readInputStreamAsString(dataHandler.getInputStream());
        config = replaceEndpoints(config);
        ByteArrayDataSource dbs = new ByteArrayDataSource(config.getBytes());
        return new DataHandler(dbs);
    }


    private boolean isBuilderEnabled() throws XPathExpressionException {
        return context.getConfigurationValue("//executionEnvironment").equals("standalone");
    }

    private boolean isClusterEnabled() throws XPathExpressionException {
        return context.getProductGroup().isClusterEnabled();
    }

    private String getExecutionEnvironment() throws XPathExpressionException {
        return context.getConfigurationValue("//executionEnvironment");
    }

    private boolean isProxyWSDlExist(String serviceUrl, long synchronizingDelay)
            throws Exception {
        return new ServiceDeploymentUtil().isServiceWSDlExist(serviceUrl, synchronizingDelay);

    }

    private boolean isProxyWSDlNotExist(String serviceUrl, long synchronizingDelay)
            throws Exception {

        return new ServiceDeploymentUtil().isServiceWSDlNotExist(serviceUrl, synchronizingDelay);

    }

    private String replaceEndpoints(String config) throws XPathExpressionException {
       //this should be AS context
        String serviceUrl = new AutomationContext("AS", TestUserMode.SUPER_TENANT_ADMIN).getContextUrls().getServiceUrl();

        config = config.replace("http://localhost:9000/services/"
                , serviceUrl);
        config = config.replace("http://127.0.0.1:9000/services/"
                , serviceUrl);
        return config;
    }

    protected OMElement replaceEndpoints(String relativePathToConfigFile, String serviceName,
                                         String port)
            throws XMLStreamException, FileNotFoundException, XPathExpressionException {
        String config = esbUtils.loadResource(relativePathToConfigFile).toString();
        config = config.replace("http://localhost:" + port + "/services/" + serviceName,
                                getBackEndServiceUrl(serviceName));

        return AXIOMUtil.stringToOM(config);
    }

    private String readInputStreamAsString(InputStream in)
            throws IOException {

        BufferedInputStream bis = new BufferedInputStream(in);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int result = bis.read();
        while (result != -1) {
            byte b = (byte) result;
            buf.write(b);
            result = bis.read();
        }
        return buf.toString();
    }


    protected String login(AutomationContext context)
            throws IOException, XPathExpressionException, URISyntaxException, SAXException,
                   XMLStreamException, LoginAuthenticationExceptionException {
        LoginLogoutClient loginLogoutClient = new LoginLogoutClient(context);
        return loginLogoutClient.login();
    }

    protected String getSessionCookie() {
        return sessionCookie;
    }
    //todo - getting role as the user
    protected String[] getUserRole(){
        return new String[]{"admin"};
    }
}

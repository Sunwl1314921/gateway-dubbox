/**
 * 
 */
package com.aldb.gateway.core.support;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.CodingErrorAction;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Lookup;
import org.apache.http.config.MessageConstraints;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestWriterFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParser;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParserFactory;
import org.apache.http.impl.nio.conn.ManagedNHttpClientConnectionFactory;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.NHttpMessageWriterFactory;
import org.apache.http.nio.conn.ManagedNHttpClientConnection;
import org.apache.http.nio.conn.NHttpConnectionFactory;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.EntityUtils;

import com.aldb.gateway.core.OpenApiHttpClientService;

/**
 * @author 如果需要实现真正的异步，则前面调用部分也需要重构，这个工作量太大。
 * 
 * 所以在这里调用还需要进行异步变同步，当前请求线程需要等待相应的响应返回值，
 * 并没有提高性，所以在生产应用中，一般情况网关与后端的服务处于同一个网段或机房内，
 * 使用同步调用可能更好。
 * 
 */
public class OpenApiHttpAsynClientServiceImpl implements OpenApiHttpClientService {

    
    private static Log logger = LogFactory.getLog(OpenApiHttpAsynClientServiceImpl.class);
    private CloseableHttpAsyncClient httpAsyncClient;

    /**
     * 绕过验证
     * 
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    private SSLContext createIgnoreVerifySSL() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sc = SSLContext.getInstance("SSLv3");

        // 实现一个X509TrustManager接口，用于绕过验证，不用修改里面的方法
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] paramArrayOfX509Certificate,
                    String paramString) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] paramArrayOfX509Certificate,
                    String paramString) throws CertificateException {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };
        sc.init(null, new TrustManager[] { trustManager }, null);
        return sc;
    }

    private String username = "";
    private String password = "";

    private void initHttpAsynClient() throws IOReactorException {
        // Use custom message parser / writer to customize the way HTTP
        // messages are parsed from and written out to the data stream.
        NHttpMessageParserFactory<HttpResponse> responseParserFactory = new DefaultHttpResponseParserFactory() {

            @Override
            public NHttpMessageParser<HttpResponse> create(final SessionInputBuffer buffer,
                    final MessageConstraints constraints) {
                LineParser lineParser = new BasicLineParser() {

                    @Override
                    public Header parseHeader(final CharArrayBuffer buffer) {
                        try {
                            return super.parseHeader(buffer);
                        } catch (ParseException ex) {
                            return new BasicHeader(buffer.toString(), null);
                        }
                    }

                };
                return new DefaultHttpResponseParser(buffer, lineParser, DefaultHttpResponseFactory.INSTANCE,
                        constraints);
            }

        };
        NHttpMessageWriterFactory<HttpRequest> requestWriterFactory = new DefaultHttpRequestWriterFactory();

        // Use a custom connection factory to customize the process of
        // initialization of outgoing HTTP connections. Beside standard
        // connection
        // configuration parameters HTTP connection factory can define message
        // parser / writer routines to be employed by individual connections.
        NHttpConnectionFactory<ManagedNHttpClientConnection> connFactory = new ManagedNHttpClientConnectionFactory(
                requestWriterFactory, responseParserFactory, HeapByteBufferAllocator.INSTANCE);

        // Client HTTP connection objects when fully initialized can be bound to
        // an arbitrary network socket. The process of network socket
        // initialization,
        // its connection to a remote address and binding to a local one is
        // controlled
        // by a connection socket factory.

        // SSL context for secure connections can be created either based on
        // system or application specific properties.
        // SSLContext sslcontext =
        // org.apache.http.ssl.SSLContexts.createSystemDefault();
        // SSLContext sslcontext =
        // org.apache.http.ssl.SSLContexts.createDefault();
        SSLContext sslcontext = null;
        try {
            sslcontext = this.createIgnoreVerifySSL();
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Use custom hostname verifier to customize SSL hostname verification.
        HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();

        // Create a registry of custom connection session strategies for
        // supported
        // protocol schemes.
        Registry<SchemeIOSessionStrategy> sessionStrategyRegistry = RegistryBuilder.<SchemeIOSessionStrategy> create()
                .register("http", NoopIOSessionStrategy.INSTANCE)
                // .register("https", new SSLIOSessionStrategy(sslcontext,
                // hostnameVerifier)).build();
                .register("https", new SSLIOSessionStrategy(sslcontext)).build();
        // .register("https",
        // SSLConnectionSocketFactory.getSystemSocketFactory()).build();
        // Use custom DNS resolver to override the system DNS resolution.
        DnsResolver dnsResolver = new SystemDefaultDnsResolver() {

            @Override
            public InetAddress[] resolve(final String host) throws UnknownHostException {
                if (host.equalsIgnoreCase("myhost")) {
                    return new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) };
                } else {
                    return super.resolve(host);
                }
            }

        };

        // Create I/O reactor configuration
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors()).setConnectTimeout(30000)
                .setSoTimeout(30000).build();

        // Create a custom I/O reactort
        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

        // Create a connection manager with custom configuration.
        PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor,
                connFactory, sessionStrategyRegistry, dnsResolver);

        // Create message constraints
        MessageConstraints messageConstraints = MessageConstraints.custom().setMaxHeaderCount(200)
                .setMaxLineLength(2000).build();
        // Create connection configuration
        ConnectionConfig connectionConfig = ConnectionConfig.custom().setMalformedInputAction(CodingErrorAction.IGNORE)
                .setUnmappableInputAction(CodingErrorAction.IGNORE).setCharset(Consts.UTF_8)
                .setMessageConstraints(messageConstraints).build();
        // Configure the connection manager to use connection configuration
        // either
        // by default or for a specific host.
        connManager.setDefaultConnectionConfig(connectionConfig);
        // connManager.setConnectionConfig(new HttpHost("somehost", 80),
        // ConnectionConfig.DEFAULT);

        // Configure total max or per route limits for persistent connections
        // that can be kept in the pool or leased by the connection manager.
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(10);
        // connManager.setMaxPerRoute(new HttpRoute(new HttpHost("somehost",
        // 80)), 20);

        // Use custom cookie store if necessary.
        CookieStore cookieStore = new BasicCookieStore();
        // Use custom credentials provider if necessary.
        /*
         * CredentialsProvider credentialsProvider = new
         * BasicCredentialsProvider(); credentialsProvider.setCredentials(new
         * AuthScope("localhost", 8889), new
         * UsernamePasswordCredentials("squid", "nopassword"));
         */

        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        // Create global request configuration
        RequestConfig defaultRequestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT)
                .setExpectContinueEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST))
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC)).build();

        Lookup<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider> create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
                .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory()).build();
        // Create an HttpClient with the given custom dependencies and
        // configuration.
        // CloseableHttpAsyncClient
        httpAsyncClient = HttpAsyncClients.custom().setConnectionManager(connManager).setDefaultCookieStore(cookieStore)
                .setDefaultCredentialsProvider(credentialsProvider)
                // .setProxy(new HttpHost("localhost", 8889))
                .setDefaultAuthSchemeRegistry(authSchemeRegistry).setDefaultRequestConfig(defaultRequestConfig).build();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    httpAsyncClient.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

    }

    /*
     * private CloseableHttpAsyncClient getHttpClient() { return
     * httpAsyncClient;
     * 
     * }
     */

    /*
     * private static class FutureCallbackImpl implements
     * FutureCallback<HttpResponse> {
     * 
     * private String body; private CountDownLatch latch;
     * 
     * public FutureCallbackImpl(String body, CountDownLatch latch) { this.body
     * = body; this.latch = latch; }
     * 
     * public void completed(final HttpResponse response) { //
     * System.out.println(httpget.getRequestLine() + "->" + //
     * response.getStatusLine()) try { HttpEntity entity = response.getEntity();
     * int statusCode = response.getStatusLine().getStatusCode(); if (statusCode
     * == HttpStatus.SC_OK) { if (entity != null) { body =
     * EntityUtils.toString(entity, "utf-8"); System.out.println("body======" +
     * body); } EntityUtils.consume(entity); } } catch (ParseException e) { //
     * TODO Auto-generated catch block e.printStackTrace(); } catch (IOException
     * e) { // TODO Auto-generated catch block e.printStackTrace(); }
     * latch.countDown(); }
     * 
     * public void failed(final Exception ex) {
     * 
     * System.out.println("failed....."); latch.countDown(); }
     * 
     * public void cancelled() { System.out.println(" cancelled....");
     * latch.countDown(); }
     * 
     * }
     */

    public void init() throws Exception {
        initHttpAsynClient();

    }

    private static class FutureCallbackImpl implements FutureCallback<HttpResponse> {

        public FutureCallbackImpl(CountDownLatch latch) {
            this.latch = latch;
        }

        CountDownLatch latch;
        private String responseBody;

        @Override
        public void completed(HttpResponse response) {
            try {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK) {
                    // 获取结果实体
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        // 按指定编码转换结果实体为String类型
                        responseBody = EntityUtils.toString(entity, "utf-8");
                    }
                    EntityUtils.consume(entity);
                }

            } catch (Exception e) {
            } finally {
                latch.countDown();
            }
        }

        @Override
        public void failed(Exception ex) {
            // TODO Auto-generated method stub

        }

        @Override
        public void cancelled() {
            // TODO Auto-generated method stub

        }

        public String getBody() {
            return this.responseBody;
        }
    }

    @Override
    public String doGet(String webUrl, Map<String, String> headParams) {

        org.apache.http.client.methods.HttpGet httpGet = new org.apache.http.client.methods.HttpGet(webUrl);
        if (headParams != null) {
            for (Map.Entry<String, String> entry : headParams.entrySet()) {
                httpGet.setHeader(entry.getKey(), entry.getValue());
            }
        }
        // 异步变同步，
        CountDownLatch latch = new CountDownLatch(1);
        FutureCallbackImpl fi = new FutureCallbackImpl(latch);
        this.httpAsyncClient.start();
        this.httpAsyncClient.execute(httpGet, fi);
        try {
            latch.await();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        httpGet.releaseConnection();
        return fi.getBody();
    }

    @Override
    public String doGet(String webUrl, Map<String, String> headParams, Map<String, String> paramMap) {
        logger.info(String.format("run doGet method,weburl=%s", webUrl));
        String url = webUrl;
        // 设置编码格式
        String queryString = createLinkString(paramMap);
        url = url + "?" + queryString;
        return doGet(url, headParams);
     
    }
    /**
     * 把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串
     * 
     * @param params
     *            需要排序并参与字符拼接的参数组
     * @return 拼接后字符串
     */
    private String createLinkString(Map<String, String> params) {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);
        String prestr = "";
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);

            if (i == keys.size() - 1) {// 拼接时，不包括最后一个&字符
                prestr = prestr + key + "=" + value;
            } else {
                prestr = prestr + key + "=" + value + "&";
            }
        }
        return prestr;
    }

    @Override
    public String doHttpsGet(String webUrl, Map<String, String> headParams) {
        return doGet(webUrl, headParams);
    }

    @Override
    public String doHttpsGet(String webUrl, Map<String, String> headParams, Map<String, String> paramMap) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String doHttpsPost(String url, Map<String, String> headParams, String requestData) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String doPost(String url, Map<String, String> headParams, String requestData) {
        // TODO Auto-generated method stub
        return null;
    }

}

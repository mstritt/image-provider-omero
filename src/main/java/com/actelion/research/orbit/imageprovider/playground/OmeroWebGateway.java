/*
 *     Orbit, a versatile image analysis software for biological image-based quantification.
 *     Copyright (C) 2009 - 2018 Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.actelion.research.orbit.imageprovider.playground;

import com.actelion.research.orbit.imageprovider.OmeroConf;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;


/**
 * Omero WebGateway API calls, e.g. /webgateway/archived_files/download/
 *
 */
public class OmeroWebGateway {

    private OmeroConf omeroConf;
    private String username;
    private String password;

    public OmeroWebGateway(OmeroConf omeroConf, String username, String password) {
        this.omeroConf = omeroConf;
        this.username = username;
        this.password = password;
    }

    /**
     * HttpClient which works with untrusted SSL certificates. For use in production the standard HttpClient should be used.
     */
    private HttpClient getHttpClient(){
        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory> create();
        ConnectionSocketFactory plainSF = new PlainConnectionSocketFactory();
        registryBuilder.register("http", plainSF);
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            TrustStrategy anyTrustStrategy = new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    return true;
                }
            };
            SSLContext sslContext = SSLContexts.custom().useTLS().loadTrustMaterial(trustStore, anyTrustStrategy)
                    .build();
            LayeredConnectionSocketFactory sslSF = new SSLConnectionSocketFactory(sslContext,
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            registryBuilder.register("https", sslSF);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Registry<ConnectionSocketFactory> registry = registryBuilder.build();
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(registry);
        HttpClient httpclient = HttpClientBuilder.create().setConnectionManager(connManager).build();

        return httpclient;
    }


    public WebContext createWebContext() throws IOException {
        HttpClient httpClient = getHttpClient();
        BasicCookieStore cookieStore = new BasicCookieStore();
        cookieStore.clear();
        BasicHttpContext httpContext = new BasicHttpContext();
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE,cookieStore);

        // get token
        HttpGet request1 = new HttpGet(omeroConf.getWebURL()+"/api/v0/token/");
        HttpResponse response1 = httpClient.execute(request1,httpContext);
        String responseString = IOUtils.toString(response1.getEntity().getContent(), "UTF-8");
        String token = responseString.substring(10,responseString.length()-2);
        //System.out.println("token: "+token);
        System.out.println("token retrieved");

        // login

        // HttpPost request = new HttpPost("https://localhost:444/api/v0/token/");
        //   HttpPost request = new HttpPost("https://localhost:444/api/v0/servers/");
        HttpPost request = new HttpPost(omeroConf.getWebURL()+"/api/v0/login/");
        //   HttpPost request = new HttpPost("https://localhost:444/webclient/login/");

        List<NameValuePair> nvpList = new ArrayList<>();
        nvpList.add(new BasicNameValuePair("username",username));
        nvpList.add(new BasicNameValuePair("password",password));
        nvpList.add(new BasicNameValuePair("server",String.valueOf(omeroConf.getServerNumber())));
        nvpList.add(new BasicNameValuePair("'noredirect'","1"));
        StringEntity entity = new UrlEncodedFormEntity(nvpList);
        request.setEntity(entity);
        request.setHeader("X-CSRFToken",token);
        request.setHeader("Referer",omeroConf.getWebURL());
        HttpResponse response = httpClient.execute(request,httpContext);
        //String responseString2 = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
        System.out.println("http context created");

        WebContext webContext = new WebContext(httpClient, httpContext, token);
        return webContext;
    }


    public void downloadImage(int imageId, OutputStream outputStream, WebContext webContext) throws IOException {
        String downloadUrl = omeroConf.getWebURL()+"/webgateway/archived_files/download/"+imageId;
        HttpGet request = new HttpGet(downloadUrl);
        HttpResponse response = webContext.getHttpClient().execute(request,webContext.getHttpContext());
        response.getEntity().writeTo(outputStream);
    }


    class WebContext implements AutoCloseable {
        private HttpClient httpClient;
        private HttpContext httpContext;
        private String token;

        public WebContext(HttpClient httpClient, HttpContext httpContext, String token) {
            this.httpClient = httpClient;
            this.httpContext = httpContext;
            this.token = token;
        }

        public void close() throws IOException {
            // logout
            HttpPost request = new HttpPost(omeroConf.getWebURL()+"/api/v0/logout/");
            request.setHeader("X-CSRFToken",token);
            request.setHeader("Referer",omeroConf.getWebURL());
            HttpResponse response = httpClient.execute(request,httpContext);
            if (response!=null) System.out.println("web context closed");
            //String responseString = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
            //System.out.println(responseString);
        }

        public HttpClient getHttpClient() {
            return httpClient;
        }

        public void setHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        public HttpContext getHttpContext() {
            return httpContext;
        }

        public void setHttpContext(HttpContext httpContext) {
            this.httpContext = httpContext;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }



    public static void main(String[] args) throws IOException {
        OmeroWebGateway omeroWebGateway = new OmeroWebGateway(new OmeroConf("localhost",4064,444,true,1000,"","",1),"root","password");
        try (WebContext webContext = omeroWebGateway.createWebContext()) {
             // download image
            long startt = System.currentTimeMillis();
            omeroWebGateway.downloadImage(1,new FileOutputStream("d:/test.ndpi"),webContext);
            long usedt = System.currentTimeMillis()-startt;
            System.out.println("used time: "+usedt/1000+"s");
        }
    }

}

/**
 * 
 */
package com.aldb.gateway.core;

import java.util.Map;


/**
 * @author Administrator
 *
 */
public interface OpenApiHttpClientService {

    // get请求
    public String doGet(String webUrl,Map<String,String> headParams);
    public String doGet(String webUrl,Map<String,String> headParams, Map<String,String> paramMap);
    public String doHttpsGet(String webUrl,Map<String,String> headParams);
    
    public String doHttpsGet(String webUrl, Map<String,String> headParams,Map<String,String> paramMap);
    

    
    //post请求
    public String doHttpsPost(String url,Map<String,String> headParams, String requestData);

   // public String doPost(String url, String reqData, String contentType,String traceId);
    
    
    public String doPost(String url,Map<String,String> headParams,String requestData);
    
    
    /*public Map<String, String> HttpGet(String webUrl, Map paramMap);

    public Map<String, String> HttpGet(String url, String method, Map paramMap);
    */
    /*public String doPost(String url, String reqData, String contentType, String params);
    public String HttpPost(String webUrl, Map paramMap);

    public String HttpPost(String url, String method, Map paramMap);
*/
}
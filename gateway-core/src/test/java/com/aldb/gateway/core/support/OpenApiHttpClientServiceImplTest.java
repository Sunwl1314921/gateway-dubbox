/**
 * 
 */
package com.aldb.gateway.core.support;

import java.util.HashMap;

import org.junit.Test;

/**
 * @author sunff
 *
 */
public class OpenApiHttpClientServiceImplTest {

    
    
   @Test
    public void t() {
        OpenApiHttpClientServiceImpl p=new OpenApiHttpClientServiceImpl();
        p.init();
       // System.out.println(p.doHttpsGet("https://www.baidu.com/",new HashMap<String,String>()));
        
        System.out.println(p.doGet("http://ent.sina.com.cn/zl/bagua/2017-12-08/doc-ifypnsin8934039.shtml", new HashMap<String,String>()));
       }
}

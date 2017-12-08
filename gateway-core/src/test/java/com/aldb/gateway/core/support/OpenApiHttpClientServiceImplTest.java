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
        System.out.println(p.doHttpsGet("https://www.baidu.com/",new HashMap<String,String>()));
       }
}

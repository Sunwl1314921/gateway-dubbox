/**
 * 
 */
package com.aldb.gateway.core.support;

import org.junit.Test;

/**
 * @author sunff
 *
 */
public class OpenApiHttpAsynClientServiceImplTest {

    
    
    @Test
    public void doGet() throws Exception {
        OpenApiHttpAsynClientServiceImpl p=new OpenApiHttpAsynClientServiceImpl();
        p.init();
        String body=p.doGet("http://www.baidu.com", null);
        System.out.println(body);
    }
   

}

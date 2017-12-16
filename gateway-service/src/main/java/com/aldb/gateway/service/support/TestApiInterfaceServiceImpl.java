/**
 * 
 */
package com.aldb.gateway.service.support;

import com.aldb.gateway.common.OpenApiRouteBean;
import com.aldb.gateway.service.ApiInterfaceService;
import com.aldb.gateway.service.entity.ApiInfo;

/**
 * @author sunff
 *
 */
public class TestApiInterfaceServiceImpl implements ApiInterfaceService{

    @Override
    public ApiInfo queryApiInterfaceByApiId(OpenApiRouteBean bean) {
        
        
        ApiInfo a= new ApiInfo();
        a.setHostAddress("ent.sina.com.cn");
        a.setProtocol("http");
       // a.setTargetUrl("/");
        return a;
    }

}

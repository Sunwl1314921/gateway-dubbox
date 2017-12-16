/**
 * 
 */
package com.aldb.gateway.service;

import com.aldb.gateway.common.OpenApiRouteBean;
import com.aldb.gateway.service.entity.ApiInfo;

/**
 * @author Administrator
 *
 */
public interface ApiInterfaceService {

    /**
     * 根据apiId及版本号，获取一个后端服务api接口服务的信息
     * @param apiId
     * @param version
     * @return
     */
    ApiInfo queryApiInterfaceByApiId(OpenApiRouteBean bean); //这个接口方法暂定两个
    
    
}

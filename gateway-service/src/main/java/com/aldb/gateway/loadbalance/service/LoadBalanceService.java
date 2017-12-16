/**
 * 
 */
package com.aldb.gateway.loadbalance.service;

import java.util.List;

import com.aldb.gateway.common.OpenApiRouteBean;

/**
 * @author Administrator
 *
 */
public interface LoadBalanceService {

    
    String chooseOne(OpenApiRouteBean bean,List<String> set);
}

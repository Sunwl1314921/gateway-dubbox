/**
 * 
 */
package com.aldb.gateway.handler;

import com.aldb.gateway.protocol.AbstractTask;

/**
 * @author Administrator
 *
 */
@Deprecated
public interface ThreadPoolHandler {

    
    public Object addTask(AbstractTask task);
}

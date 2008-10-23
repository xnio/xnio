/**
 *
 */
package org.jboss.xnio.management;

/**
 * 
 */
public interface NioTcpPropertiesMBean extends BasicCountersMBean{

    public String getLocalAddress();

    public int getLocalPort();

    public String getRemoteAddress();

    public int getRemotePort();

}

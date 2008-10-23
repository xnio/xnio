/**
 *
 */
package org.jboss.xnio.management;

/**
 * 
 */
public interface NioUdpPropertiesMBean extends BasicCountersMBean{

    public String getLocalAddress();

    public int getLocalPort();

    public String[] getRemoteAddresses();

}

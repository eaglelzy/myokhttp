package com.lizy.okhttp;

import com.lizy.okhttp.internal.Util;
import com.lizy.okhttp.internal.http.RecordingProxySelector;

import org.junit.Test;

import java.util.List;

import javax.net.SocketFactory;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by lizy on 16-8-30.
 */
public class AddressTest {
    private Dns dns = Dns.SYSTEM;
    private SocketFactory socketFactory = SocketFactory.getDefault();
    private Authenticator authenticator = Authenticator.NONE;
    private List<ConnectionSpec> connectionSpecs = Util.immutableList();
    private List<Protocol> protocols = Util.immutableList(Protocol.HTTP_1_1);
    private RecordingProxySelector proxySelector = new RecordingProxySelector();

    @Test
    public void equalsAndHashCode() throws Exception {
        Address a = new Address("github.com", 80, dns, socketFactory, authenticator, protocols,
                connectionSpecs, proxySelector, null, null, null, null);
        Address b = new Address("github.com", 80, dns, socketFactory, authenticator, protocols,
                connectionSpecs, proxySelector, null, null, null, null);
        Address c = new Address("github.com", 443, dns, socketFactory, authenticator, protocols,
                connectionSpecs, proxySelector, null, null, null, null);

        Address d = new Address("github.com", 80, dns, socketFactory, authenticator, protocols,
                connectionSpecs, new RecordingProxySelector(), null, null, null, null);
        assertTrue(a.equals(b));
        assertTrue(a.hashCode() == b.hashCode());
        assertFalse(a.equals(c));
        assertFalse(a.hashCode() == c.hashCode());

        assertFalse(a.equals(d));
        assertFalse(a.hashCode() == d.hashCode());
    }
}

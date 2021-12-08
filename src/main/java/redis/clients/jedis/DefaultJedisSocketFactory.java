package redis.clients.jedis;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.util.IOUtils;

public class DefaultJedisSocketFactory implements JedisSocketFactory {

  protected static final HostAndPort DEFAULT_HOST_AND_PORT = new HostAndPort(Protocol.DEFAULT_HOST,
      Protocol.DEFAULT_PORT);

  private volatile HostAndPort hostAndPort = DEFAULT_HOST_AND_PORT;
  private int connectionTimeout = Protocol.DEFAULT_TIMEOUT;
  private int socketTimeout = Protocol.DEFAULT_TIMEOUT;
  private boolean ssl = false;
  private SSLSocketFactory sslSocketFactory = null;
  private SSLParameters sslParameters = null;
  private HostnameVerifier hostnameVerifier = null;
  private HostAndPortMapper hostAndPortMapper = null;

  public DefaultJedisSocketFactory() {
  }

  public DefaultJedisSocketFactory(HostAndPort hostAndPort) {
    this(hostAndPort, null);
  }

  public DefaultJedisSocketFactory(JedisClientConfig config) {
    this(null, config);
  }

  @Deprecated
  public DefaultJedisSocketFactory(String host, int port, int connectionTimeout, int socketTimeout,
      boolean ssl, SSLSocketFactory sslSocketFactory, SSLParameters sslParameters,
      HostnameVerifier hostnameVerifier) {
    this.hostAndPort = new HostAndPort(host, port);
    this.connectionTimeout = connectionTimeout;
    this.socketTimeout = socketTimeout;
    this.ssl = ssl;
    this.sslSocketFactory = sslSocketFactory;
    this.sslParameters = sslParameters;
    this.hostnameVerifier = hostnameVerifier;
  }

  public DefaultJedisSocketFactory(HostAndPort hostAndPort, JedisClientConfig config) {
    if (hostAndPort != null) {
      this.hostAndPort = hostAndPort;
    }
    if (config != null) {
      this.connectionTimeout = config.getConnectionTimeoutMillis();
      this.socketTimeout = config.getSocketTimeoutMillis();
      this.ssl = config.isSsl();
      this.sslSocketFactory = config.getSslSocketFactory();
      this.sslParameters = config.getSslParameters();
      this.hostnameVerifier = config.getHostnameVerifier();
      this.hostAndPortMapper = config.getHostAndPortMapper();
    }
  }

  private void connectToFirstSuccsefulHost(Socket socket, HostAndPort hostAndPort) throws IOException {
    List<InetAddress> hosts = Arrays.asList(InetAddress.getAllByName(hostAndPort.getHost()));
    Collections.shuffle(hosts);
    List<ConnectException> connectExceptions = new ArrayList<ConnectException>();
    boolean connected = false;
    for (InetAddress host : hosts) {
      try{
        socket.connect(new InetSocketAddress(host.getHostAddress(), hostAndPort.getPort()), connectionTimeout);
        connected = true;
        break;
      } catch (ConnectException e) {
        connectExceptions.add(e);
      }
    }
    if(connected == false) {
      throw new ConnectException(connectExceptions.toString());
    }
  }

  @Override
  public Socket createSocket() throws JedisConnectionException {
    Socket socket = null;
    try {
      socket = new Socket();
      socket.setReuseAddress(true);
      socket.setKeepAlive(true); // Will monitor the TCP connection is valid
      socket.setTcpNoDelay(true); // Socket buffer Whetherclosed, to ensure timely delivery of data
      socket.setSoLinger(true, 0); // Control calls close () method, the underlying socket is closed immediately
      HostAndPort _hostAndPort = getSocketHostAndPort();
      connectToFirstSuccsefulHost(socket, hostAndPort);
      if (ssl) {
        SSLSocketFactory _sslSocketFactory = this.sslSocketFactory;
        if (null == _sslSocketFactory) {
          _sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
        socket = _sslSocketFactory.createSocket(socket, _hostAndPort.getHost(), _hostAndPort.getPort(), true);
        if (null != sslParameters) {
          ((SSLSocket) socket).setSSLParameters(sslParameters);
        }
        if (null != hostnameVerifier
            && !hostnameVerifier.verify(_hostAndPort.getHost(), ((SSLSocket) socket).getSession())) {
          String message = String.format(
            "The connection to '%s' failed ssl/tls hostname verification.", _hostAndPort.getHost());
          throw new JedisConnectionException(message);
        }
      }

      return socket;

    } catch (IOException ex) {

      IOUtils.closeQuietly(socket);

      throw new JedisConnectionException("Failed to create socket.", ex);
    }
  }

  public void updateHostAndPort(HostAndPort hostAndPort) {
    this.hostAndPort = hostAndPort;
  }

  public HostAndPort getHostAndPort() {
    return this.hostAndPort;
  }

  protected HostAndPort getSocketHostAndPort() {
    HostAndPortMapper mapper = hostAndPortMapper;
    HostAndPort hap = this.hostAndPort;
    if (mapper != null) {
      HostAndPort mapped = mapper.getHostAndPort(hap);
      if (mapped != null) {
        return mapped;
      }
    }
    return hap;
  }

  @Override
  public String toString() {
    return "DefaultJedisSocketFactory{" + hostAndPort.toString() + "}";
  }
}

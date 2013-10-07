package net.i2p.data;

/*
 * free (adj.): unencumbered; not under the control of others
 * Written by jrandom in 2003 and released into the public domain 
 * with no warranty of any kind, either expressed or implied.  
 * It probably won't make your computer catch on fire, or eat 
 * your children, but it might.  Use at your own risk.
 *
 */

import java.io.InputStream;
import java.io.IOException;
import java.util.Map;

import net.i2p.I2PAppContext;
import net.i2p.util.LHMCache;
import net.i2p.util.SystemVersion;

/**
 * Defines an end point in the I2P network.  The Destination may move around
 * in the network, but messages sent to the Destination will find it
 *
 * Note that the public (encryption) key is essentially unused, since
 * "end-to-end" encryption was removed in 0.6. The public key in the
 * LeaseSet is used instead.
 *
 * The first bytes of the public key are used for the IV for leaseset encryption,
 * but that encryption is poorly designed and should be deprecated.
 *
 * As of 0.9.9 this data structure is immutable after the two keys and the certificate
 * are set; attempts to change them will throw an IllegalStateException.
 *
 * @author jrandom
 */
public class Destination extends KeysAndCert {

    private String _cachedB64;

    //private static final boolean STATS = true;
    private static final int CACHE_SIZE;
    private static final int MIN_CACHE_SIZE = 32;
    private static final int MAX_CACHE_SIZE = 512;
    static {
        long maxMemory = SystemVersion.getMaxMemory();
        CACHE_SIZE = (int) Math.min(MAX_CACHE_SIZE, Math.max(MIN_CACHE_SIZE, maxMemory / 512*1024));
        //if (STATS)
        //    I2PAppContext.getGlobalContext().statManager().createRateStat("DestCache", "Hit rate", "Router", new long[] { 10*60*1000 });
    }

    private static final Map<SigningPublicKey, Destination> _cache = new LHMCache(CACHE_SIZE);

    /**
     * Pull from cache or return new
     * @since 0.9.9
     */
    public static Destination create(InputStream in) throws DataFormatException, IOException {
        PublicKey pk = PublicKey.create(in);
        SigningPublicKey sk = SigningPublicKey.create(in);
        Certificate c = Certificate.create(in);
        Destination rv;
        synchronized(_cache) {
            rv = _cache.get(sk);
        }
        if (rv != null && rv.getPublicKey().equals(pk) && rv.getCertificate().equals(c)) {
            //if (STATS)
            //    I2PAppContext.getGlobalContext().statManager().addRateData("DestCache", 1);
            return rv;
        }
        //if (STATS)
        //    I2PAppContext.getGlobalContext().statManager().addRateData("DestCache", 0);
        rv = new Destination(pk, sk, c);
        synchronized(_cache) {
            _cache.put(sk, rv);
        }
        return rv;
    }

    public Destination() {}

    /**
     * alternative constructor which takes a base64 string representation
     * @param s a Base64 representation of the destination, as (eg) is used in hosts.txt
     */
    public Destination(String s) throws DataFormatException {
        fromBase64(s);
    }

    /**
     * @since 0.9.9
     */
    private Destination(PublicKey pk, SigningPublicKey sk, Certificate c) {
        _publicKey = pk;
        _signingKey = sk;
        _certificate = c;
    }

    /**
     *  deprecated, used only by Packet.java in streaming
     *  @return the written length (NOT the new offset)    
     */    
    public int writeBytes(byte target[], int offset) {
        int cur = offset;
        System.arraycopy(_publicKey.getData(), 0, target, cur, PublicKey.KEYSIZE_BYTES);
        cur += PublicKey.KEYSIZE_BYTES;
        System.arraycopy(_signingKey.getData(), 0, target, cur, SigningPublicKey.KEYSIZE_BYTES);
        cur += SigningPublicKey.KEYSIZE_BYTES;
        cur += _certificate.writeBytes(target, cur);
        return cur - offset;
    }
    
    /**
     * @deprecated, was used only by Packet.java in streaming, now unused
     *
     * @throws IllegalStateException if data already set
     */
    public int readBytes(byte source[], int offset) throws DataFormatException {
        if (source == null) throw new DataFormatException("Null source");
        if (source.length <= offset + PublicKey.KEYSIZE_BYTES + SigningPublicKey.KEYSIZE_BYTES) 
            throw new DataFormatException("Not enough data (len=" + source.length + " off=" + offset + ")");
        if (_publicKey != null || _signingKey != null || _certificate != null)
            throw new IllegalStateException();
        int cur = offset;
        
        _publicKey = PublicKey.create(source, cur);
        cur += PublicKey.KEYSIZE_BYTES;
        
        _signingKey = SigningPublicKey.create(source, cur);
        cur += SigningPublicKey.KEYSIZE_BYTES;
        
        _certificate = Certificate.create(source, cur);
        cur += _certificate.size();
        
        return cur - offset;
    }

    public int size() {
        return PublicKey.KEYSIZE_BYTES + SigningPublicKey.KEYSIZE_BYTES + _certificate.size();
    }

    /**
     *  Cache it.
     *  Useful in I2PTunnelHTTPServer where it is added to the headers
     *  @since 0.9.9
     */
    @Override
    public String toBase64() {
        if (_cachedB64 == null)
            _cachedB64 = super.toBase64();
        return _cachedB64;
    }

    /**
     *  Clear the cache.
     *  @since 0.9.9
     */
    public static void clearCache() {
        synchronized(_cache) {
            _cache.clear();
        }
    }
}

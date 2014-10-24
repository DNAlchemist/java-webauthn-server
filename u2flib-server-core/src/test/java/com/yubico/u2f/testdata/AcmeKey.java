package com.yubico.u2f.testdata;

import com.yubico.u2f.TestUtils;
import org.apache.commons.codec.binary.Base64;

import java.security.cert.X509Certificate;

import static com.yubico.u2f.TestUtils.fetchCertificate;

public class AcmeKey {

  public static final X509Certificate ATTESTATION_CERTIFICATE =
          fetchCertificate(GnubbyKey.class.getResourceAsStream("acme/attestation-certificate.der"));

  public static final String CLIENT_DATA_BASE64 = TestVectors.CLIENT_DATA_REGISTER_BASE64;
  public static final byte[] REGISTRATION_DATA = TestUtils.parseHex(
          "0504478E16BBDBBB741A660A000314A8B6BD63095196ED704C52EEBC0FA02A61"
                  + "8F19FF59DF18451A11CEE43DEFD9A29B5710F63DFC671F752B1B0C6CA76C8427"
                  + "AF2D403C2415E1760D1108105720C6069A9039C99D09F76909C36D9EFC350937"
                  + "31F85F55AC6D73EA69DE7D9005AE9507B95E149E19676272FC202D949A3AB151"
                  + "B96870308201443081EAA0030201020209019189FFFFFFFF5183300A06082A86"
                  + "48CE3D040302301B3119301706035504031310476E756262792048534D204341"
                  + "2030303022180F32303132303630313030303030305A180F3230363230353331"
                  + "3233353935395A30303119301706035504031310476F6F676C6520476E756262"
                  + "7920763031133011060355042D030A00019189FFFFFFFF51833059301306072A"
                  + "8648CE3D020106082A8648CE3D030107034200041F1302F12173A9CBEA83D06D"
                  + "755411E582A87FBB5850EDDCF3607EC759A4A12C3CB392235E8D5B17CAEE1B34"
                  + "E5B5EB548649696257F0EA8EFB90846F88AD5F72300A06082A8648CE3D040302"
                  + "0349003046022100B4CAEA5DC60FBF9F004ED84FC4F18522981C1C303155C082"
                  + "74E889F3F10C5B23022100FAAFB4F10B92F4754E3B08B5AF353F78485BC903EC"
                  + "E7EA911264FC1673B6598F3046022100F3BE1BF12CBF0BE7EAB5EA32F3664EDB"
                  + "18A24D4999AAC5AA40FF39CF6F34C9ED022100CE72631767367467DFE2AECF6A"
                  + "5A4EBA9779FAC65F5CA8A2C325B174EE4769AC");
  public static final String REGISTRATION_DATA_BASE64 = Base64
          .encodeBase64URLSafeString(REGISTRATION_DATA);
  public static final byte[] KEY_HANDLE = TestUtils.parseHex(
          "3c2415e1760d1108105720c6069a9039c99d09f76909c36d9efc35093731f85f"
                  + "55ac6d73ea69de7d9005ae9507b95e149e19676272fc202d949a3ab151b96870");
  public static final byte[] USER_PUBLIC_KEY = TestUtils.parseHex(
          "04478e16bbdbbb741a660a000314a8b6bd63095196ed704c52eebc0fa02a618f"
                  + "19ff59df18451a11cee43defd9a29b5710f63dfc671f752b1b0c6ca76c8427af"
                  + "2d");
}
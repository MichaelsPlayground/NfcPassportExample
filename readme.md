# NFC Passport example

This app reads a passport and displays data like surname and name, birthday and some more.

When a face image is stored on the chip it will be displayed as well.

It is running on Android 13.

The program is tested with a German passport. A German ID-Card (nPA) does NOT work with this program.

You need to provide these 3 data fields neccessary to get access to the chip data:
- passport number
- birth date (format yymmdd)
- expiration date (format yymmdd)

You can save or load one dataset using **Encrypted Shared Preferences**.

The code is partially taken from https://github.com/alimertozdemir/EPassportNFCReader with original dependencies

This is a version with **updated dependencies**. Please do not update "jnbis" as it will break the program.

updated dependencies:
```plaintext
    implementation 'org.jmrtd:jmrtd:0.7.35'
    implementation 'net.sf.scuba:scuba-sc-android:0.0.23'
    implementation 'edu.ucar:jj2000:5.2'
    // Java Implementation of NIST Biometric Image Software (NBIS)
    implementation group: 'com.github.mhshams', name: 'jnbis', version: '1.1.0' // do not update
```

original dependencies:
```plaintext
    implementation 'org.jmrtd:jmrtd:0.7.18'
    implementation 'net.sf.scuba:scuba-sc-android:0.0.20'
    implementation 'com.madgag.spongycastle:prov:1.58.0.0'
    implementation 'edu.ucar:jj2000:5.2'
    implementation 'com.github.mhshams:jnbis:1.1.0'
```
package de.androidcrypto.nfcpassportexample;

import static org.jmrtd.PassportService.DEFAULT_MAX_BLOCKSIZE;
import static org.jmrtd.PassportService.NORMAL_MAX_TRANCEIVE_LENGTH;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import net.sf.scuba.smartcards.CardFileInputStream;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;

import org.jmrtd.BACKey;
import org.jmrtd.BACKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardSecurityFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.DG2File;
import org.jmrtd.lds.icao.MRZInfo;
import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;
//import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private final String TAG = "NfcPassportAct";

    TextView tv1;
    ImageView ivPhoto;

    Button clearPassportData, loadPassportData, savePassportData;
    com.google.android.material.textfield.TextInputEditText etPassportNumber, etBirthDate, etExpirationDate;
    com.google.android.material.textfield.TextInputEditText etData, etLog;
    private View loadingLayout;

    byte[] tagId;
    private NfcAdapter mNfcAdapter;
    final String TechIsoDep = "android.nfc.tech.IsoDep";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(myToolbar);

        clearPassportData = findViewById(R.id.btnClearPassportData);
        loadPassportData = findViewById(R.id.btnLoadPassportData);
        savePassportData = findViewById(R.id.btnSavePassportData);
        etPassportNumber = findViewById(R.id.etPassportNumber);
        etBirthDate = findViewById(R.id.etBirthDate);
        etExpirationDate = findViewById(R.id.etExpirationDate);

        tv1 = findViewById(R.id.tv1);
        etData = findViewById(R.id.etData);
        etLog = findViewById(R.id.etLog);
        ivPhoto = findViewById(R.id.ivPhoto);
        loadingLayout = findViewById(R.id.loading_layout);

        // init encrypted shared preferences
        EncryptedSharedPreferencesUtils.setupEncryptedSharedPreferences(getApplicationContext());

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        savePassportData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String passNumber = etPassportNumber.getText().toString().toUpperCase();
                String passBirthDate = etBirthDate.getText().toString();
                String passExpirationDate = etExpirationDate.getText().toString();
                if (TextUtils.isEmpty(passNumber)) {
                    showSnackbarRed(view, "please enter a passport number");
                }
                if (TextUtils.isEmpty(passBirthDate)) {
                    showSnackbarRed(view, "please enter a birth date");
                }
                if (TextUtils.isEmpty(passExpirationDate)) {
                    showSnackbarRed(view, "please enter an expiration date");
                }
                EncryptedSharedPreferencesUtils.savePassportData(
                        passNumber,
                        passBirthDate,
                        passExpirationDate
                );
                showSnackbarGreen(view, "The passport data are saved");
            }
        });

        loadPassportData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                etPassportNumber.setText(EncryptedSharedPreferencesUtils.loadPassportNumber());
                etBirthDate.setText(EncryptedSharedPreferencesUtils.loadPassportBirthDate());
                etExpirationDate.setText(EncryptedSharedPreferencesUtils.loadPassportExpirationDate());
            }
        });

        clearPassportData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                etPassportNumber.setText("");
                etBirthDate.setText("");
                etExpirationDate.setText("");
            }
        });
    }

    /**
     * section for NFC
     */

    /**
     * This method is run in another thread when a card is discovered
     * This method cannot cannot direct interact with the UI Thread
     * Use `runOnUiThread` method to change the UI from this method
     *
     * @param tag discovered tag
     */
    @Override
    public void onTagDiscovered(Tag tag) {
        runOnUiThread(() -> {
            etLog.setText("");
            etData.setText("");
        });

        writeToUiAppend(etLog, "NFC tag discovered");

        setLoadingLayoutVisibility(true);
        // Make a Sound
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150, 10));
        } else {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(200);
        }
        tagId = tag.getId();
        writeToUiAppend(etLog, "TagId: " + BinaryUtils.bytesToHex(tagId));
        String[] techList = tag.getTechList();
        writeToUiAppend(etLog, "TechList found with these entries:");
        for (int i = 0; i < techList.length; i++) {
            writeToUiAppend(etLog, techList[i]);
            System.out.println("TechList: " + techList[i]);
        }
        // the next steps depend on the TechList found on the device
        for (int i = 0; i < techList.length; i++) {
            String tech = techList[i];
            writeToUiAppend(etLog, "");
            switch (tech) {
                case TechIsoDep: {

                    readIsoDepPassport(tag);
                    break;
                }
                default: {
                    writeToUiAppend(etLog, "*** Tech ***");
                    writeToUiAppend(etLog, "unknown tech: " + tech);
                    break;
                }
            }
        }
    }

    private void readIsoDepPassport(Tag tag) {
        Log.i(TAG, "read a tag with IsoDep technology for Passport");
        IsoDep nfc = null;
        nfc = IsoDep.get(tag);
        if (nfc != null) {
            setLoadingLayoutVisibility(true);
            nfc.setTimeout(100000);
            PersonDetails personDetails = new PersonDetails();
            String passportNumber = etPassportNumber.getText().toString();
            String birthDate = etBirthDate.getText().toString();
            String expirationDate = etExpirationDate.getText().toString();
            if (passportNumber != null && !passportNumber.isEmpty()
                    && expirationDate != null && !expirationDate.isEmpty()
                    && birthDate != null && !birthDate.isEmpty()) {
                BACKeySpec bacKey = new BACKey(passportNumber, birthDate, expirationDate);

                try {
                    CardService cardService = CardService.getInstance(nfc);
                    cardService.open();

                    PassportService service = new PassportService(cardService, NORMAL_MAX_TRANCEIVE_LENGTH, DEFAULT_MAX_BLOCKSIZE, true, false);
                    service.open();
                    boolean paceSucceeded = false;

                    try {
                        CardSecurityFile cardSecurityFile = new CardSecurityFile(service.getInputStream(PassportService.EF_CARD_SECURITY));
                        Collection<SecurityInfo> securityInfoCollection = cardSecurityFile.getSecurityInfos();
                        for (SecurityInfo securityInfo : securityInfoCollection) {
                            if (securityInfo instanceof PACEInfo) {
                                PACEInfo paceInfo = (PACEInfo) securityInfo;
                                service.doPACE(bacKey, paceInfo.getObjectIdentifier(), PACEInfo.toParameterSpec(paceInfo.getParameterId()), null);
                                paceSucceeded = true;
                            }
                        }
                    } catch (CardServiceException e) {
                        Log.e(TAG, "CardServiceException: " + e);
                        Log.e(TAG, e.getMessage());
                        //throw new RuntimeException(e);
                    } catch (IOException e) {
                        Log.e(TAG, "IOException: " + e);
                        //throw new RuntimeException(e);
                    }

                    service.sendSelectApplet(paceSucceeded);
                    if (!paceSucceeded) {
                        try {
                            service.getInputStream(PassportService.EF_COM).read();
                        } catch (Exception e) {
                            service.doBAC(bacKey);
                        }
                    }

                    CardFileInputStream dg1In = service.getInputStream(PassportService.EF_DG1);
                    DG1File dg1File = new DG1File(dg1In);

                    MRZInfo mrzInfo = dg1File.getMRZInfo();
                    personDetails.setName(mrzInfo.getSecondaryIdentifier().replace("<", " ").trim());
                    personDetails.setSurname(mrzInfo.getPrimaryIdentifier().replace("<", " ").trim());
                    personDetails.setPersonalNumber(mrzInfo.getPersonalNumber());
                    personDetails.setGender(mrzInfo.getGender().toString());
                    personDetails.setBirthDate(DateUtil.convertFromMrzDate(mrzInfo.getDateOfBirth()));
                    personDetails.setExpiryDate(DateUtil.convertFromMrzDate(mrzInfo.getDateOfExpiry()));
                    personDetails.setSerialNumber(mrzInfo.getDocumentNumber());
                    personDetails.setNationality(mrzInfo.getNationality().replace("<", " ").trim());
                    personDetails.setIssuerAuthority(mrzInfo.getIssuingState().replace("<", " ").trim());

                    // -- Face Image -- //
                    try {
                        CardFileInputStream dg2In = service.getInputStream(PassportService.EF_DG2);
                        DG2File dg2File = new DG2File(dg2In);
                        List<FaceInfo> faceInfos = dg2File.getFaceInfos();
                        List<FaceImageInfo> allFaceImageInfos = new ArrayList<>();
                        for (FaceInfo faceInfo : faceInfos) {
                            allFaceImageInfos.addAll(faceInfo.getFaceImageInfos());
                        }

                        if (!allFaceImageInfos.isEmpty()) {
                            FaceImageInfo faceImageInfo = allFaceImageInfos.iterator().next();
                            Image image = ImageUtil.getImage(MainActivity.this, faceImageInfo);
                            personDetails.setFaceImage(image.getBitmapImage());
                            personDetails.setFaceImageBase64(image.getBase64Image());
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "IOEx FaceImage: " + e.getMessage());
                    }

                } catch (CardServiceException e) {
                    Log.e(TAG, "CardServiceException: " + e);
                    Log.e(TAG, e.getMessage());
                    //throw new RuntimeException(e);
                } catch (IOException e) {
                    Log.e(TAG, "IOException: " + e);
                }
            }

            // output
            StringBuilder sb = new StringBuilder();
            sb.append("Passport data").append("\n");
            sb.append("Surname: ").append(personDetails.getSurname()).append("\n");
            sb.append("Name: ").append(personDetails.getName()).append("\n");
            sb.append("PersonalNumber: ").append(personDetails.getPersonalNumber()).append("\n");
            sb.append("Gender: ").append(personDetails.getGender()).append("\n");
            sb.append("BirthDate: ").append(personDetails.getBirthDate()).append("\n");
            sb.append("ExpiryDate: ").append(personDetails.getExpiryDate()).append("\n");
            sb.append("SerialNumber: ").append(personDetails.getSerialNumber()).append("\n");
            sb.append("Nationality: ").append(personDetails.getNationality()).append("\n");
            sb.append("IssuerAuthority: ").append(personDetails.getIssuerAuthority()).append("\n");
            writeToUiAppend(etData, sb.toString());
            if (!TextUtils.isEmpty(personDetails.getFaceImageBase64())) {
                //Bitmap image = ImageUtil.scaleImage(personDetails.getFaceImage());
                Bitmap image = personDetails.getFaceImage();
                runOnUiThread(() -> {
                    ivPhoto.setImageBitmap(image);
                });
            }
            setLoadingLayoutVisibility(false);
        }
    }

    /**
     * section for activity workflow - important is the disabling of the ReaderMode when activity is pausing
     */

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {

            if (!mNfcAdapter.isEnabled())
                showWirelessSettings();

            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            // Enable ReaderMode for all types of card and disable platform sounds
            // the option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag afer reading
            mNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableReaderMode(this);
    }

    /**
     * section for UI
     */

    private void writeToUiAppend(TextView textView, String message) {
        runOnUiThread(() -> {
            if (TextUtils.isEmpty(textView.getText().toString())) {
                textView.setText(message);
            } else {
                String newString = textView.getText().toString() + "\n" + message;
                textView.setText(newString);
            }
        });
    }

    private void writeToUiAppendReverse(TextView textView, String message) {
        runOnUiThread(() -> {
            if (TextUtils.isEmpty(textView.getText().toString())) {
                textView.setText(message);
            } else {
                String newString = message + "\n" + textView.getText().toString();
                textView.setText(newString);
            }
        });
    }

    private void writeToUiToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(),
                    message,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void setLoadingLayoutVisibility(boolean isVisible) {
        runOnUiThread(() -> {
            if (isVisible) {
                loadingLayout.setVisibility(View.VISIBLE);
            } else {
                loadingLayout.setVisibility(View.GONE);
            }
        });
    }

    private void showSnackbarGreen(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(ContextCompat.getColor(view.getContext(), R.color.green));
        snackbar.show();
    }

    private void showSnackbarRed(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(ContextCompat.getColor(view.getContext(), R.color.red));
        snackbar.show();
    }
}
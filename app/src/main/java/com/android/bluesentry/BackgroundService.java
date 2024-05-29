package com.android.bluesentry;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final long LOCATION_UPDATE_INTERVAL = 10 * 60 * 1000; // 10 minutes
    private static final long IMAGE_CAPTURE_INTERVAL = 30 * 60 * 1000; // 30 minutes
    private static final int NOTIFICATION_ID = 123;

    private Timer locationTimer;
    private Timer imageCaptureTimer;
    private List<Address> addresses;
    private int width = 1920;
    private int height = 1080;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        startLocationUpdates();
        startImageCapture();
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "CHANNEL_ID")
                .setContentTitle("Background Service")
                .setContentText("Running")
                .setSmallIcon(R.drawable.mobile_protection);

        return builder.build();
    }

    private void startLocationUpdates() {
        locationTimer = new Timer();
        locationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchLocation();
            }
        }, 0, LOCATION_UPDATE_INTERVAL);
    }

    private void startImageCapture() {
        imageCaptureTimer = new Timer();
        imageCaptureTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                captureImage();
            }
        }, 0, IMAGE_CAPTURE_INTERVAL);
    }

    private void captureImage() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0]; // Assuming only one camera
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Camera permission not granted");
                return;
            }
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    try {
                        camera.createCaptureSession(Collections.singletonList(
                                ImageReader.newInstance(width, height, ImageFormat.JPEG, 1).getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                try {
                                    CaptureRequest.Builder captureRequestBuilder =
                                            session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                    captureRequestBuilder.addTarget(
                                            ImageReader.newInstance(width, height, ImageFormat.JPEG, 1).getSurface());

                                    // Set additional parameters if needed
                                    // For example, setting auto-focus and auto-exposure modes
                                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                    session.capture(captureRequestBuilder.build(),
                                            new CameraCaptureSession.CaptureCallback() {
                                                @Override
                                                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                                               @NonNull CaptureRequest request,
                                                                               @NonNull TotalCaptureResult result) {
                                                    Image capturedImage =
                                                            ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
                                                                    .acquireLatestImage();
                                                    processCapturedImage(capturedImage);
                                                }
                                            }, null);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                Log.e(TAG, "Failed to configure camera capture session");
                            }
                        }, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.e(TAG, "Camera disconnected");
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void processCapturedImage(Image image) {
        if (image != null) {
            Bitmap bitmap = convertImageToBitmap(image);
            saveBitmapToStorage(bitmap);
            image.close();
            sendEmailWithDeviceDetails();
        }
    }

    private Bitmap convertImageToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private void saveBitmapToStorage(Bitmap bitmap) {
        File file = new File(getFilesDir(), "captured_image.jpg");
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fetchLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location != null) {

            Log.d(TAG, "Location: " + location.getLatitude() + ", " + location.getLongitude());
            getAddressUsingLocation(location);
        }
    }

    private void getAddressUsingLocation(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());

        try {
            addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressStringBuilder = new StringBuilder();
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    addressStringBuilder.append(address.getAddressLine(i));
                    if (i < address.getMaxAddressLineIndex()) {
                        addressStringBuilder.append(", ");
                    }
                }
                String fullAddress = addressStringBuilder.toString();
                Log.d(TAG, "Address: " + fullAddress);
            } else {
                Log.d(TAG, "No address found for the given location");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address", e);
        }
    }

    // Get the default email from user to send the email
    private void sendEmailWithDeviceDetails() {
        String userId = mAuth.getCurrentUser().getUid();
        getEmailFromDatabase(userId);
    }

    private void getEmailFromDatabase(String userId) {
        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String email = snapshot.child("email").getValue(String.class);
                    if (email != null) {
                        sendEmail(email);
                    } else {
                        Log.e(TAG, "Email not found in database for user: " + userId);
                    }
                } else {
                    Log.e(TAG, "User not found in database: " + userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error: " + error.getMessage());
            }
        });
    }

    private void sendEmail(String recipientEmail) {
        GetPassword getPassword = new GetPassword();
        String senderEmail = "noreply@bluesentry-7533a.firebaseapp.com";
        String smtpHost = "bluesentry-7533a.firebaseapp.com";
        int smtpPort = 587;
        String smtpUsername = getPassword.getPassword();
        String smtpPassword = "your_smtp_password"; // replace with the actual password

        Properties properties = new Properties();
        properties.put("mail.smtp.host", smtpHost);
        properties.put("mail.smtp.port", smtpPort);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
            message.setSubject("Device Details");

            String deviceLastLocation = addresses != null && !addresses.isEmpty() ? addresses.get(0).getAddressLine(0) : "Unknown";
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String imei = null;
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                imei = telephonyManager.getImei();
            }
            String deviceModel = Build.MODEL;
            String deviceManufacturer = Build.MANUFACTURER;

            String deviceDetails = "IMEI: " + imei + "\nModel: " + deviceModel + "\nManufacturer: " + deviceManufacturer + " \n Last Location: " + deviceLastLocation;

            String emailContent = "Device Details:\n" + deviceDetails;

            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(emailContent);

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);

            // Attach the captured image
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(new File(getFilesDir(), "captured_image.jpg"));
            multipart.addBodyPart(attachmentPart);

            message.setContent(multipart);

            Transport.send(message);

            Log.d(TAG, "Email sent successfully");
        } catch (MessagingException | IOException e) {
            Log.e(TAG, "Failed to send email", e);
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationTimer != null) {
            locationTimer.cancel();
        }
        if (imageCaptureTimer != null) {
            imageCaptureTimer.cancel();
        }
    }
}

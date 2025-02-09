package com.braintreepayments.api;

import android.app.Activity;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.braintreepayments.api.exceptions.AuthorizationException;
import com.braintreepayments.api.exceptions.BraintreeException;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.UnexpectedException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.HttpResponseCallback;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCallback;
import com.braintreepayments.api.interfaces.PaymentMethodNoncesUpdatedListener;
import com.braintreepayments.api.internal.BraintreeHttpClient;
import com.braintreepayments.api.models.AndroidPayCardNonce;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.CardNonce;
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.PayPalAccountBuilder;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.TokenizationKey;
import com.braintreepayments.testutils.BraintreeActivityTestRule;
import com.braintreepayments.api.test.TestActivity;
import com.braintreepayments.testutils.FixturesHelper;
import com.braintreepayments.testutils.TestClientTokenBuilder;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static com.braintreepayments.api.BraintreeFragmentTestUtils.getFragment;
import static com.braintreepayments.api.BraintreeFragmentTestUtils.getMockFragment;
import static com.braintreepayments.api.BraintreeFragmentTestUtils.tokenize;
import static com.braintreepayments.testutils.CardNumber.VISA;
import static com.braintreepayments.testutils.FixturesHelper.stringFromFixture;
import static com.braintreepayments.testutils.TestTokenizationKey.TOKENIZATION_KEY;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class TokenizationClientTest {

    @Rule
    public final BraintreeActivityTestRule<TestActivity> mActivityTestRule =
            new BraintreeActivityTestRule<>(TestActivity.class);

    private Activity mActivity;

    @Before
    public void setUp() {
        mActivity = mActivityTestRule.getActivity();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void getPaymentMethodNonces_returnsAnEmptyListIfEmpty() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        BraintreeFragment fragment = getFragment(mActivity, new TestClientTokenBuilder().build());
        fragment.addListener(new PaymentMethodNoncesUpdatedListener() {
            @Override
            public void onPaymentMethodNoncesUpdated(List<PaymentMethodNonce> paymentMethodNonces) {
                assertEquals(0, paymentMethodNonces.size());
                latch.countDown();
            }
        });

        TokenizationClient.getPaymentMethodNonces(fragment);

        latch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void getPaymentMethodNonces_throwsAnError() throws ErrorWithResponse,
            BraintreeException, InterruptedException, InvalidArgumentException {
        final CountDownLatch latch = new CountDownLatch(1);
        BraintreeFragment fragment = getMockFragment(mActivity, mock(Configuration.class));
        when(fragment.getHttpClient()).thenReturn(new BraintreeHttpClient(
                TokenizationKey.fromString(TOKENIZATION_KEY)) {
            @Override
            public void get(String path, HttpResponseCallback callback) {
                callback.failure(new UnexpectedException("Mock"));
            }
        });
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onError(Exception error) {
                assertTrue(error instanceof UnexpectedException);
                assertEquals("Mock", error.getMessage());
                latch.countDown();
            }
        });

        TokenizationClient.getPaymentMethodNonces(fragment);

        latch.await();
    }

    @Test(timeout = 1000)
    @SmallTest
    public void getPaymentMethodNonces_fetchesPaymentMethods()
            throws InvalidArgumentException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        BraintreeFragment fragment = getMockFragment(mActivity, mock(Configuration.class));
        when(fragment.getHttpClient()).thenReturn(new BraintreeHttpClient(
                TokenizationKey.fromString(TOKENIZATION_KEY)) {
            @Override
            public void get(String path, HttpResponseCallback callback) {
                callback.success(
                        stringFromFixture("payment_methods/get_payment_methods_response.json"));
            }
        });
        fragment.addListener(new PaymentMethodNoncesUpdatedListener() {
            @Override
            public void onPaymentMethodNoncesUpdated(List<PaymentMethodNonce> paymentMethodNonces) {
                assertEquals(3, paymentMethodNonces.size());
                assertEquals("11", ((CardNonce) paymentMethodNonces.get(0)).getLastTwo());
                assertEquals("PayPal", paymentMethodNonces.get(1).getTypeLabel());
                assertEquals("11", ((AndroidPayCardNonce) paymentMethodNonces.get(2)).getLastTwo());

                assertEquals(3, paymentMethodNonces.size());
                latch.countDown();
            }
        });

        TokenizationClient.getPaymentMethodNonces(fragment);

        latch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void getPaymentMethodNonces_getsPaymentMethodsFromServer() throws InterruptedException,
            InvalidArgumentException {
        final CountDownLatch latch = new CountDownLatch(1);
        final String clientToken = new TestClientTokenBuilder().build();
        BraintreeFragment fragment = BraintreeFragment.newInstance(mActivity, clientToken);
        getInstrumentation().waitForIdleSync();
        fragment.addListener(new PaymentMethodNoncesUpdatedListener() {
            @Override
            public void onPaymentMethodNoncesUpdated(List<PaymentMethodNonce> paymentMethodNonces) {
                assertEquals(1, paymentMethodNonces.size());
                assertEquals("11", ((CardNonce) paymentMethodNonces.get(0)).getLastTwo());
                latch.countDown();
            }
        });
        tokenize(fragment, new CardBuilder()
                .cardNumber(VISA)
                .expirationMonth("04")
                .expirationYear("17"));

        TokenizationClient.getPaymentMethodNonces(fragment);

        latch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void getPaymentMethodNonces_failsWithATokenizationKey() throws InterruptedException,
            InvalidArgumentException {
        final CountDownLatch latch = new CountDownLatch(1);
        BraintreeFragment fragment = BraintreeFragment.newInstance(mActivity, TOKENIZATION_KEY);
        getInstrumentation().waitForIdleSync();
        fragment.addListener(new PaymentMethodNoncesUpdatedListener() {
            @Override
            public void onPaymentMethodNoncesUpdated(List<PaymentMethodNonce> paymentMethodNonces) {
                fail("getPaymentMethodNonces succeeded");
            }
        });
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onError(Exception error) {
                assertTrue(error instanceof AuthorizationException);
                assertEquals("Tokenization key authorization not allowed for this endpoint. Please use an authentication method with upgraded permissions",
                        error.getMessage());
                latch.countDown();
            }
        });
        tokenize(fragment, new CardBuilder()
                .cardNumber(VISA)
                .expirationMonth("04")
                .expirationYear("17"));

        TokenizationClient.getPaymentMethodNonces(fragment);

        latch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void tokenize_acceptsAPayPalAccount() throws InterruptedException, JSONException {
        // TODO: I think we're passing in bad credentials for OTC flow. Probably need to the stub

        final CountDownLatch latch = new CountDownLatch(1);
        JSONObject otcJson = new JSONObject(FixturesHelper.stringFromFixture("paypal_otc_response.json"));
        BraintreeFragment fragment = getFragment(mActivity, new TestClientTokenBuilder().withPayPal().build());
        PayPalAccountBuilder paypalAccountBuilder =
                new PayPalAccountBuilder()
                        .oneTouchCoreData(otcJson);

        TokenizationClient.tokenize(fragment, paypalAccountBuilder,
                new PaymentMethodNonceCallback() {
                    @Override
                    public void success(PaymentMethodNonce paymentMethodNonce) {
                        assertNotNull(paymentMethodNonce.getNonce());
                        assertEquals("PayPal", paymentMethodNonce.getTypeLabel());
                        latch.countDown();
                    }

                    @Override
                    public void failure(Exception exception) {
                        assertTrue(exception.getMessage(), false);
                    }
                });

        latch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void tokenize_tokenizesAPayPalAccountWithATokenizationKey() throws InterruptedException, JSONException {
        final CountDownLatch latch = new CountDownLatch(1);
        BraintreeFragment fragment = getFragment(mActivity, TOKENIZATION_KEY);

        JSONObject otcJson = new JSONObject(FixturesHelper.stringFromFixture("paypal_otc_response.json"));
        PayPalAccountBuilder paypalAccountBuilder =
                new PayPalAccountBuilder().oneTouchCoreData(otcJson);

        TokenizationClient.tokenize(fragment, paypalAccountBuilder,
                new PaymentMethodNonceCallback() {
                    @Override
                    public void success(PaymentMethodNonce paymentMethodNonce) {
                        assertNotNull(paymentMethodNonce.getNonce());
                        assertEquals("PayPal", paymentMethodNonce.getTypeLabel());
                        latch.countDown();
                    }

                    @Override
                    public void failure(Exception exception) {
                        fail(exception.getMessage());
                    }
                });

        latch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void tokenize_includesSessionIdInRequest() throws InvalidArgumentException, JSONException {
        final CountDownLatch latch = new CountDownLatch(1);
        BraintreeFragment fragment = getMockFragment(mActivity, mock(Configuration.class));
        when(fragment.getSessionId()).thenReturn("session-id");

        when(fragment.getHttpClient()).thenReturn(new BraintreeHttpClient(TokenizationKey.fromString(TOKENIZATION_KEY)) {
            @Override
            public void post(String path, String data, HttpResponseCallback callback) {
                try {
                    JSONObject body = new JSONObject(data);
                    JSONObject _meta = body.getJSONObject("_meta");
                    assertNotNull(_meta);

                    String sessionId = _meta.getString("sessionId");
                    assertEquals("session-id", sessionId);
                } catch (JSONException e) {
                    fail(e.getMessage());
                }

                latch.countDown();
            }
        });

        JSONObject otcJson = new JSONObject(FixturesHelper.stringFromFixture("paypal_otc_response.json"));
        PayPalAccountBuilder paypalAccountBuilder =
                new PayPalAccountBuilder().oneTouchCoreData(otcJson);

        TokenizationClient.tokenize(fragment, paypalAccountBuilder, null);
    }
}

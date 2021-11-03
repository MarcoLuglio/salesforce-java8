package marcoluglio;

// #region apache httpclient imports
import org.apache.http.Consts;
// import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
// #endregion

// #region json response
import org.apache.http.Header;
import org.apache.commons.codec.Charsets;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
// #endregion

// #region salesforce enterprise messaging platform
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import com.salesforce.emp.connector.BayeuxParameters;
import com.salesforce.emp.connector.EmpConnector;
import com.salesforce.emp.connector.TopicSubscription;
import com.salesforce.emp.connector.example.LoggingListener;
import org.cometd.bayeux.Channel;
// #endregion

/**
 * Saleforce integration example
 */
public final class App {

	private App() {
	}

	/**
	 * Get Salesforce platform event
	 * @param args The arguments of the program.
	 */
	public static void main(String[] args) {

		// create connected app with refresh_token, offline_access and api scopes
		// POST request to https://MyDomainName.my.salesforce.com/services/oauth2/token
		// with form vars in body

		String orgDomain = "https://luceo-dev-ed.my.salesforce.com"; // https://luceo-dev-ed.lightning.force.com
		String consumerKey = "3MVG9cHH2bfKACZZye_LnBkHGZVSUs__AoR8WcELHl0oxsYZJW27Iv_P7GDPFcijHloo2gDntF0uGKnNe6QNu";
		String consumerSecret = "1FE29373577335D7AF3CFE7EEB5467658FBE5F8635A1CC158835F53AC663B472";

		// 1a - authorize interactively
		// ============================

		// String pkceKey = "TODO"; // only for /authorize requests, include this value wilh key code_challenge
		// scope:refresh_token
		// /services/oath/authorize

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) { // using apache HTTPClient library

			// ========================================
			// 2 - exchage one of these (request token)
			// ========================================

			List<NameValuePair> form = new ArrayList<>();

			// 2a - authorization flow
			// -----------------------

			/*
			String authorizationCode = "";
			String pkceVerifier = ""; // in response to pkce code challenge

			form.add(new BasicNameValuePair("grant_type", "authorization_code"));
			form.add(new BasicNameValuePair("code", authorizationCode));
			form.add(new BasicNameValuePair("code_verifier", pkceVerifier));
			// // form.add(new BasicNameValuePair("code_challenge_method", "S256")); // this cannot be changed for saleforce, it is always SHA256
			*/

			// 2b - resource owner password credentials flow
			// ---------------------------------------------

			String username = "marcodejulho@yahoo.com"; // TODO create an user just for the API
			String password = "FTdW3VcPWagpQEn";

			form.add(new BasicNameValuePair("grant_type", "password"));
			form.add(new BasicNameValuePair("username", username));
			form.add(new BasicNameValuePair("password", password));

			// common for a and b
			// ------------------

			form.add(new BasicNameValuePair("client_id", consumerKey));
			form.add(new BasicNameValuePair("client_secret", consumerSecret));

			// 2c - signed jwt bearer flow
			// ---------------------------

			// check http://tracking-softmarketing.p-email.net/track?hash=ccf026f145fb05e07b598eba2b02059ecccec414=84cd573fa5023d8d6c0c4ea5fb9082e9&action=click&value=http://200.237.184.21/
			// or https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-on-behalf-of-flow
			// or https://medium.com/@salesforce.notes/salesforce-oauth-jwt-bearer-flow-cc70bfc626c2

			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);

			HttpPost httpPost = new HttpPost(orgDomain + "/services/oauth2/token");
			httpPost.setEntity(entity);
			// httpPost.setHeader("HttpHeaders.SOME_KEY", "value");
			// httpPost.getRequestLine(); // what is that?

			// ========================================
			// 3 - for these (receive requested tokens)
			// ========================================

			ResponseHandler<String> responseHandler = response -> {

				int status = response.getStatusLine().getStatusCode();

				final int statusOkStartRange = 200;
				final int statusOkEndRange = 300;
				if (status >= statusOkStartRange && status < statusOkEndRange) {

					Header encodingHeader = entity.getContentEncoding();
					Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
					String json = EntityUtils.toString(entity, encoding);
					return json;
					//JSONObject o = new JSONObject(json); // TODO check what we are doing in the full project
					//return o;

				} else {

					throw new ClientProtocolException("Unexpected response status: " + status);

				}

			};

			String responseBody = httpClient.execute(httpPost, responseHandler);
			System.out.println(responseBody);

			// will respond with
			/*
			{
				"access_token":"00D5e000001N20Q!ASAAQEDBeG8bOwPu8NWGsvFwWNfqHOp5ZcjMpFsU6yEMxTKdBuRXNzSZ8xGVyAiY8xoy1KYkaadzRlA2F5Zd3JXqLVitOdNS",
				"instance_url":"https://MyDomainName.my.salesforce.com",
				"id":"https://login.salesforce.com/id/00D5e000001N20QEAS/0055e000003E8ooAAC",
				"token_type":"Bearer",
				"issued_at":"1627237872637",
				"signature":"jmaZOgQyqUxFKAesVPsqVfAWxI62O+aH/mJhDrc8KvQ="
			}
			*/

			// accessToken can be pregenerated, and regenerated if needed
			String accessToken = "6Cel800D5e000005p82t8885e000003vYdGEuxyyLw9bsdLNdUsjs8gb0zCaCJi5JgtpS6ZvVIBPUklOTfwJnnBkgPDtgnSxMcuzxRqfrSb";
			String refreshToken = "";
			String idToken = "";

			// platform events
			// ===============

			BayeuxParameters params = new BayeuxParameters() {

				@Override
				public String bearerToken() {
					return "TODO";
				}

				@Override
				public URL host() {
					try {
						return new URL(orgDomain + "TODO");
					} catch (MalformedURLException e) {
						throw new IllegalArgumentException(String.format("Unable to create url: %s", orgDomain + "TODO"), e);
					}
				}
			};

			// compiles fine but VS Code linter complains
			Consumer<Map<String, Object>> consumer = event -> workerThreadPool.submit(() -> System.out.println(String.format("Received:\n%s, \nEvent processed by threadName:%s, threadId: %s", JSON.toString(event), Thread.currentThread().getName(), Thread.currentThread().getId())));
			EmpConnector connector = new EmpConnector(params);

			connector.addListener(Channel.META_CONNECT, new LoggingListener(true, true))
			.addListener(Channel.META_DISCONNECT, new LoggingListener(true, true))
			.addListener(Channel.META_HANDSHAKE, new LoggingListener(true, true));

			final int secondsToWait = 5;

			connector.start().get(secondsToWait, TimeUnit.SECONDS);

			String topic = "TODO";
			long replayFrom = 0; // TODO is there a way to check this?
			TopicSubscription subscription = connector.subscribe(topic, replayFrom, consumer).get(secondsToWait, TimeUnit.SECONDS);

			System.out.println(String.format("Subscribed: %s", subscription));

		} catch (IOException ex) {

			System.out.println(ex);

		}

	}

}

package com.coast.handlers.fetch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.coast.supabase.SupabaseClient;
import okhttp3.Request;
import okhttp3.Response;

public class FetchExistingUserIdsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String url = SupabaseClient.getUrl() + "/rest/v1/users?select=user_id";
            Request request = SupabaseClient.baseRequest(url).get().build();

            try (Response res = SupabaseClient.getHttpClient().newCall(request).execute()) {
                String body = res.body().string();
                if (!res.isSuccessful()) return response(500, "{\"error\":\"" + body + "\"}");
                return response(200, body);
            }

        } catch (Exception e) {
            return response(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody(body);
    }
}

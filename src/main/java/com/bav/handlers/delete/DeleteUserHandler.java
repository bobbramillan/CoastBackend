package com.coast.handlers.delete;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.coast.supabase.SupabaseClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Map;

public class DeleteUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            Map<String, String> params = event.getQueryStringParameters();
            if (params == null || !params.containsKey("user_id")) {
                return response(400, "{\"error\":\"Missing user_id parameter\"}");
            }

            String userId = params.get("user_id");
            String url = SupabaseClient.getUrl() + "/rest/v1/users?user_id=eq." + userId;
            Request request = SupabaseClient.baseRequest(url).delete().build();

            try (Response res = SupabaseClient.getHttpClient().newCall(request).execute()) {
                if (!res.isSuccessful()) return response(500, "{\"error\":\"" + res.body().string() + "\"}");
                return response(200, "{\"success\":true}");
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

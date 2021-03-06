package de.bisquallisoft.twitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bisquallisoft.twitch.json.stream.StreamResource;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author squall
 */
public class TwitchApi {

    public static final String CLIENT_ID = "r4h4mcs056enp6p9cuiytu8p0n5f2qj";
    private String authToken;

    public static final String AUTH_URL = "https://api.twitch.tv/kraken/oauth2/authorize?response_type=token"
            + "&client_id=" + CLIENT_ID
            + "&redirect_uri=http://localhost/twitch_oauth"
            + "&scope=user_read";

    public TwitchApi(String authToken) {
        this.authToken = authToken;
    }

    /**
     *
     * @return
     * @throws de.bisquallisoft.twitch.UnauthorizedException
     */
    public List<Stream> getFollowedStreams() throws SocketTimeoutException  {
        try {
            String response = Request.Get("https://api.twitch.tv/kraken/streams/followed?limit=100")
                    .addHeader("Accept", "application/vnd.twitchtv.v3+json")
                    .addHeader("Authorization", "OAuth " + authToken)
                    .addHeader("Client-ID", CLIENT_ID)
                    .execute()
                    .returnContent().asString();

            ObjectMapper mapper = new ObjectMapper();
            StreamResource resource = mapper.readValue(response, StreamResource.class);

            return resource.getStreams().stream()
                    .map(s -> {
                        Stream stream = new Stream();
                        stream.setName(s.getChannel().getDisplay_name());
                        stream.setUrl(s.getChannel().getUrl());
                        stream.setViewers(s.getViewers());
                        stream.setPreviewImage(s.getPreview().getLarge());
                        stream.setStatus(s.getChannel().getStatus());
                        stream.setGame(s.getChannel().getGame());
                        stream.setLastUpdateTime(s.getChannel().getUpdated_at());
                        stream.setLogo(s.getChannel().getLogo());

                        return stream;
                    })
                    .collect(Collectors.toList());

        }catch (HttpResponseException hre) {
            if (hre.getStatusCode() == 401 || hre.getStatusCode() == 403) {
                throw new UnauthorizedException(hre.getMessage());
            } else {
                throw new RuntimeException("could not request users streams", hre);
            }
        } catch (IOException e) {
            throw new RuntimeException("could not request users streams", e);
        }
    }

    public boolean isAuthValid()  {
        try {
            getFollowedStreams();
            return true;
        } catch (UnauthorizedException e) {
            return false;
        } catch (SocketTimeoutException e) {
            return false;
        }
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}

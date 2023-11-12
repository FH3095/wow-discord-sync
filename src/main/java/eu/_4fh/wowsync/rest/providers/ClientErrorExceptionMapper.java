package eu._4fh.wowsync.rest.providers;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ClientErrorExceptionMapper implements ExceptionMapper<ClientErrorException> {

	@Override
	public Response toResponse(ClientErrorException exception) {
		Response response = Response.status(exception.getResponse().getStatusInfo()).type(MediaType.TEXT_HTML)
				.entity(GenericExceptionMapper.buildErrorMessage(exception.getResponse().getStatusInfo(),
						exception.getMessage()))
				.build();
		return response;
	}
}

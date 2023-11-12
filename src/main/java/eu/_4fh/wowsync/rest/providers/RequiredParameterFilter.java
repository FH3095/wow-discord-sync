package eu._4fh.wowsync.rest.providers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RequiredParameterFilter implements ContainerRequestFilter {
	@Target(ElementType.PARAMETER)
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	public static @interface RequiredParam {
	}

	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(ContainerRequestContext requestContext) {
		for (Parameter parameter : resourceInfo.getResourceMethod().getParameters()) {
			if (parameter.isAnnotationPresent(QueryParam.class) && parameter.isAnnotationPresent(RequiredParam.class)) {
				QueryParam queryAnnotation = parameter.getAnnotation(QueryParam.class);
				if (!requestContext.getUriInfo().getQueryParameters().containsKey(queryAnnotation.value())) {
					throw new BadRequestException("Missing required query parameter: " + queryAnnotation.value());
				}
			}
		}
	}
}

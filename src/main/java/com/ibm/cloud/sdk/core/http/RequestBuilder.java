/**
 * (C) Copyright IBM Corp. 2015, 2021.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.ibm.cloud.sdk.core.http;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ibm.cloud.sdk.core.util.GsonSingleton;
import com.ibm.cloud.sdk.core.util.StringHelper;
import com.ibm.cloud.sdk.core.util.Validator;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Convenience class for constructing HTTP/HTTPS requests.
 */
public class RequestBuilder {

  private static Escaper pathEscaper = UrlEscapers.urlPathSegmentEscaper();

  private enum HTTPMethod {
    DELETE, GET, POST, PUT, PATCH, HEAD
  }

  /**
   * The DELETE method requests that the origin server delete the resource identified by the Request-URI.
   *
   * @param url the URL
   *
   * @return this
   */
  public static RequestBuilder delete(HttpUrl url) {
    return new RequestBuilder(HTTPMethod.DELETE, url);
  }

  /**
   * The GET method means retrieve whatever information (in the form of an entity) is identified by the Request-URI.
   *
   * @param url the URL
   *
   * @return this
   */
  public static RequestBuilder get(HttpUrl url) {
    return new RequestBuilder(HTTPMethod.GET, url);
  }

  /**
   * The POST request method is designed to request that a web server accept the data enclosed in the request
   * message's body for storage. It is often used when uploading a file or submitting a completed web form.
   *
   * @param url the URL
   *
   * @return this
   */
  public static RequestBuilder post(HttpUrl url) {
    return new RequestBuilder(HTTPMethod.POST, url);
  }

  /**
   * The PUT method requests that the enclosed entity be stored under the supplied Request-URI.
   *
   * @param url the URL
   *
   * @return this
   */
  public static RequestBuilder put(HttpUrl url) {
    return new RequestBuilder(HTTPMethod.PUT, url);
  }

  /**
   * The PUT method requests that the enclosed entity be stored under the supplied Request-URI.
   *
   * @param url the URL
   *
   * @return this
   */
  public static RequestBuilder patch(HttpUrl url) {
    return new RequestBuilder(HTTPMethod.PATCH, url);
  }

  /**
   * The HEAD method means retrieve the headers for the resource identified by the Request-URI.
   *
   * @param url the URL
   *
   * @return this
   */
  public static RequestBuilder head(HttpUrl url) {
    return new RequestBuilder(HTTPMethod.HEAD, url);
  }

  /**
   * Creates a properly encoded HttpUrl object with no path parameters.
   *
   * @param serviceUrl the base service URL associated with the service instance
   * @param pathSegments the path segments for a specific API call
   * @return the HttpUrl object
   */
  public static HttpUrl constructHttpUrl(String serviceUrl, String[] pathSegments) {
    Validator.notEmpty(serviceUrl, "The serviceUrl cannot be null");
    HttpUrl.Builder httpUrlBuilder = HttpUrl.parse(serviceUrl).newBuilder();
    for (String segment : pathSegments) {
      if (!segment.isEmpty()) {
        httpUrlBuilder.addPathSegments(segment);
      }
    }
    return httpUrlBuilder.build();
  }

  /**
   * Creates a properly encoded HttpUrl object with path parameters.
   *
   * @param serviceUrl the base service URL associated with the service instance
   * @param pathSegments the path segments for a specific API call
   * @param pathParameters the path parameters for a specific API call
   * @return the HttpUrl object
   */
  public static HttpUrl constructHttpUrl(String serviceUrl, String[] pathSegments, String[] pathParameters) {
    Validator.notEmpty(serviceUrl, "The serviceUrl cannot be null");
    HttpUrl.Builder httpUrlBuilder = HttpUrl.parse(serviceUrl).newBuilder();
    for (int i = 0; i < pathSegments.length; i++) {
      if (!pathSegments[i].isEmpty()) {
        httpUrlBuilder.addPathSegments(pathSegments[i]);
      }
      if (i < pathParameters.length && !pathParameters[i].isEmpty()) {
        httpUrlBuilder.addPathSegment(pathParameters[i]);
      }
    }
    return httpUrlBuilder.build();
  }

  /**
   * Resolves a request URL by first resolving path parameter references within "path",
   * then it combines the serviceUrl portion with the resolved path string.
   * @param serviceUrl the base URL associated with a service instance
   * @param path the unresolved path
   * @param pathParams a map containing path parameters, keyed by the parameter name
   * @return an HttpUrl instance that holds the request URL
   */
  public static HttpUrl resolveRequestUrl(String serviceUrl, String path, Map<String, String> pathParams) {
    Validator.notEmpty(serviceUrl, "The serviceUrl cannot be null");

    // Create a builder based on the service's base URL.
    // Note: in case of an error, the "get" method will throw an exception.
    HttpUrl.Builder builder = HttpUrl.get(serviceUrl).newBuilder();

    if (StringUtils.isNotEmpty(path)) {

      // If path parameter values were passed in, then for each one, replace any references to it
      // within "path" with the path parameter's encoded value.
      if (pathParams != null) {
        for (Entry<String, String> paramEntry : pathParams.entrySet()) {
          if (StringUtils.isEmpty(paramEntry.getValue())) {
            throw new IllegalArgumentException(
                String.format("Path parameter '%s' is empty", paramEntry.getKey()));
          }

          // Encode the individual path param value as a path segment, then replace its reference(s)
          // within the path string with the encoded value.
          String encodedValue = pathEscaper.escape(paramEntry.getValue());
          String ref = String.format("{%s}", paramEntry.getKey());
          path = path.replace(ref, encodedValue);
        }
      }

      // Strip off any leading slash from the path string.
      if (path.startsWith("/")) {
        path = path.substring(1);
      }

      // Set the path on the builder object.
      builder.addEncodedPathSegments(path);
    }

    // Return the final HttpUrl object.
    return builder.build();
  }

  /**
   * Similar to the three-arg version of resolveRequestUrl, but supports
   * a "path" parameter with no path param references.
   * @param serviceUrl the base URL associated with a service instance
   * @param path the path string
   * @return an HttpUrl instance that holds the request URL
   */
  public static HttpUrl resolveRequestUrl(String serviceUrl, String path) {
    return resolveRequestUrl(serviceUrl, path, null);
  }

  private RequestBody body;
  private HttpUrl httpUrl;
  private final List<NameValue> formParams = new ArrayList<NameValue>();
  private final List<NameValue> headers = new ArrayList<NameValue>();
  private final HTTPMethod method;
  private final List<NameValue> queryParams = new ArrayList<NameValue>();

  /**
   * Instantiates a new request.
   *
   * @param method the method, PUT, POST, GET or DELETE
   * @param url the request URL
   */
  private RequestBuilder(HTTPMethod method, HttpUrl url) {
    this.method = method;
    if (url == null) {
      throw new IllegalArgumentException("url cannot be null");
    }

    this.httpUrl = url;
  }

  /**
   * Adds a key/value pair.
   *
   * <pre>
   * <code>
   * Request r = new Request.get("https://foo.bar").add("singleParam", "value")
   *   .add("multiParam", new String[] { "1", "2", "3" })
   *   .add("singleParamWithOutValue", null);
   * </code>
   * </pre>
   *
   * @param params the parameters
   * @param name the parameter name
   * @param value the value to set, will be obtained via {@link String#valueOf(boolean)}. If null, only the parameter
   * is set. It can also be a collection or array, in which case all elements are added as query parameters
   *
   * @return this
   */
  private RequestBuilder add(List<NameValue> params, String name, Object value) {
    if (value instanceof Iterable) {
      for (final Object o : (Iterable<?>) value) {
        addParam(params, name, o);
      }
    } else if (value instanceof Object[]) {
      for (final Object o : (Object[]) value) {
        addParam(params, name, o);
      }
    } else {
      addParam(params, name, value);
    }
    return this;
  }

  /**
   * Adds the name, value par to the parameter list as <b>BasicNameValue</b>.
   *
   * @param params the parameter list
   * @param name the parameter name
   * @param value the parameter value
   */
  private void addParam(List<NameValue> params, String name, Object value) {
    params.add(new NameValue(name, value == null ? null : String.valueOf(value)));
  }

  /**
   * Builds the request.
   *
   * @return the request
   */
  public Request build() {
    final Request.Builder builder = new Request.Builder();
    // URL
    builder.url(toUrl());

    if (method == HTTPMethod.GET || method == HTTPMethod.HEAD) {
      Validator.isNull(body, "cannot send a RequestBody in a GET or HEAD request");
    } else if (!formParams.isEmpty()) {
      // The current behaviour of the RequestBuilder is to replace the body when formParams is
      // present
      final FormBody.Builder formBody = new FormBody.Builder();
      for (final NameValue param : formParams) {
        final String value = param.getValue() != null ? param.getValue() : "";
        formBody.add(param.getName(), value);
      }
      body = formBody.build();
    } else if (body == null) {
      // POST/PUT require a body so send an empty body if the actual is null
      // DELETE allows an empty request body
      body = RequestBody.create(null, new byte[0]);
    }
    builder.method(method.name(), body);

    // accept application/json by default
    builder.header(HttpHeaders.ACCEPT, HttpMediaType.APPLICATION_JSON);

    for (final NameValue header : headers) {
      builder.header(header.getName(), header.getValue());
    }

    return builder.build();
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "RequestBuilder [method=" + method + ", formParams=" + formParams + ", headers=" + headers
        + ", queryParams=" + queryParams + ", httpUrl=" + httpUrl.toString() + "]";
  }

  /**
   * Return the request url including query parameters.
   *
   * @return the string
   */
  public String toUrl() {
    final HttpUrl.Builder builder = httpUrl.newBuilder();
    for (final NameValue param : queryParams) {
      builder.addQueryParameter(param.getName(), param.getValue());
    }
    return builder.build().uri().toString();
  }

  /**
   * Adds a name-value par to a given list.
   *
   * @param params a list of parameters
   * @param args a list of arguments
   *
   * @return this
   */
  private RequestBuilder with(List<NameValue> params, Object... args) {
    if (args != null) {
      Validator.isTrue((args.length % 2) == 0, "need even number of arguments");
      for (int i = 0; i < args.length; i += 2) {
        add(params, args[i].toString(), args[i + 1]);
      }
    }
    return this;
  }

  /**
   * Sets the body.
   *
   * @param body the body
   * @return the request builder
   */
  public RequestBuilder body(RequestBody body) {
    this.body = body;
    return this;
  }

  /**
   * Sets the string content to the request (used with POST/PUT). This will encapsulate the string into a
   * {@link RequestBody} encoded with UTF-8
   *
   * @param content the content to POST/PUT
   * @param contentType the HTTP contentType to use.
   *
   * @return this
   */
  public RequestBuilder bodyContent(String content, String contentType) {
    return body(RequestBody.create(MediaType.parse(contentType), content));
  }

  /**
   * Sets the file content (InputStream) to the request (used with POST/PUT).
   *
   * @param stream the InputStream to read the request body content from
   * @param contentType the contentType associated with the data read from the InputStream
   * @return this
   */
  public RequestBuilder bodyContent(InputStream stream, String contentType) {
    return body(InputStreamRequestBody.create(MediaType.parse(contentType), stream));
  }


  /**
   * Sets the request body content from one of three different sources.
   * The three input sources are used in this precedence order:
   * <ol>
   * <li>If 'jsonContent' is not null, then use that.</li>
   * <li>If 'jsonPatchContent' is not null, then use that.</li>
   * <li>Else use 'nonJsonContent'.
   * </ol>
   *
   * @param contentType
   *      the value of the "Content-Type" header associated with the request body
   * @param jsonContent
   *      a model instance to be serialized and used for the request body
   * @param jsonPatchContent
   *      a collection of JsonPatchOperation instances to be serialized and used for the request body
   * @param nonJsonContent
   *      an InputStream whose contents should be used directly as the request body
   * @return this
   */
  public RequestBuilder bodyContent(String contentType, Object jsonContent, Object jsonPatchContent,
    InputStream nonJsonContent) {
    if (contentType != null) {
      Gson requestGson = GsonSingleton.getGsonWithoutPrettyPrinting().newBuilder().create();
      if (jsonContent != null) {
        this.bodyContent(requestGson.toJson(jsonContent), contentType);
      } else if (jsonPatchContent != null) {
        this.bodyContent(requestGson.toJson(jsonPatchContent), contentType);
      } else {
        this.bodyContent(nonJsonContent, contentType);
      }
    }
    return this;
  }

  /**
   * Sets the request body content from one of three different sources.
   * The three input sources are used in this precedence order:
   * <ol>
   * <li>If 'jsonContent' is not null, then use that.</li>
   * <li>If 'jsonPatchContent' is not null, then use that.</li>
   * <li>Else use 'nonJsonContent'.
   * </ol>
   *
   * @param contentType
   *      the value of the "Content-Type" header associated with the request body
   * @param jsonContent
   *      a model instance to be serialized and used for the request body
   * @param jsonPatchContent
   *      a collection of JsonPatchOperation instances to be serialized and used for the request body
   * @param nonJsonContent
   *      a string to be used directly as the request body
   * @return this
   */
  public RequestBuilder bodyContent(String contentType, Object jsonContent, Object jsonPatchContent,
                                    String nonJsonContent) {
    InputStream nonJson = null;
    if (nonJsonContent != null) {
      nonJson = StringHelper.toInputStream(nonJsonContent);
    }

    return bodyContent(contentType, jsonContent, jsonPatchContent, nonJson);
  }

  /**
   * Adds a JSON content to the request (used with POST/PUT). This will encapsulate the json into a
   * {@link RequestBody} encoded with UTF-8 and use {@code "application/json"} as Content-Type
   *
   * @param json the JsonObject json
   *
   * @return this
   */
  public RequestBuilder bodyJson(JsonObject json) {
    return bodyContent(json.toString(), HttpMediaType.APPLICATION_JSON);
  }

  /**
   * Adds a JSON content to the request (used with POST/PUT/PATCH). This will encapsulate the json into a
   * {@link RequestBody} encoded with UTF-8 and use {@code "application/json"} as Content-Type
   *
   * @param json the JsonObject json
   * @param mediaType the contentType value
   *
   * @return this
   */
  public RequestBuilder bodyJson(JsonObject json, String mediaType) {
    return bodyContent(json.toString(), mediaType);
  }

  /**
   * Adds form parameters.
   *
   * @param args a list of name-value form parameters
   *
   * @return this
   */
  public RequestBuilder form(Object... args) {
    return with(formParams, args);
  }

  /**
   * Adds header parameters.
   *
   * @param args a list of name-value headers
   *
   * @return this
   */
  public RequestBuilder header(Object... args) {
    return with(headers, args);
  }

  /**
   * Adds query parameters.
   *
   * @param args a list of name-value query parameters
   *
   * @return this
   */
  public RequestBuilder query(Object... args) {
    return with(queryParams, args);
  }
}

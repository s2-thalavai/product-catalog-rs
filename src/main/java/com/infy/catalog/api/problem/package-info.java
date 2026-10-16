/**
 * RFC 9457 "Problem Details for HTTP APIs". The XML form uses the namespace defined by the RFC.
 */
@XmlSchema(namespace = "urn:ietf:rfc:7807", elementFormDefault = XmlNsForm.QUALIFIED)
package com.infy.catalog.api.problem;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;

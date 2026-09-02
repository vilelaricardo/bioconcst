FROM eclipse-temurin:17-jdk

# JAVA_TOOL_OPTIONS is read by the JVM launcher itself, so it applies to every
# java process started in this image - both ValiPar's own JVM and the target
# benchmark process it spawns internally.
#   -Xverify:none                          works around a ClassFormatError in
#                                           ValiPar's ASM-based instrumentation
#                                           (bad constant-pool Class entries)
#   --add-opens java.base/java.util=...    needed by the bundled cloning-1.8.5
#                                           lib, which reflects into java.util
#                                           internals (blocked by JPMS)
#   -Djava.net.preferIPv4Stack=true         a DatagramSocket() binding to the
#                                           IPv6 wildcard produces an address
#                                           with colons that collides with
#                                           ValiPar's own ":" delimited UDDI
#                                           registration protocol
ENV JDK_JAVA_OPTIONS="-Xverify:none --add-opens java.base/java.util=ALL-UNNAMED -Djava.net.preferIPv4Stack=true"

COPY vendor/ /opt/valipar/
RUN chmod +x /opt/valipar/bin/valipar

ENV PATH="/opt/valipar/bin:${PATH}"

WORKDIR /work
ENTRYPOINT ["valipar"]

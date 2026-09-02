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

# Matches the host user's UID/GID so files this container writes into the
# bind-mounted ./experiment directory come out owned by that same host user
# instead of root - on native Linux (unlike Docker Desktop's macOS VM, which
# already remaps this transparently) a root-owned file on a bind mount can't
# be deleted by the host user without sudo. Pass --build-arg HOST_UID=$(id -u)
# --build-arg HOST_GID=$(id -g) when building on a native Linux host; the
# defaults below are only a fallback for a build that doesn't pass them.
ARG HOST_UID=1000
ARG HOST_GID=1000
RUN (getent group ${HOST_GID} || groupadd -g ${HOST_GID} valipar) \
    && useradd -m -u ${HOST_UID} -g ${HOST_GID} -s /bin/bash valipar 2>/dev/null || true
USER ${HOST_UID}:${HOST_GID}

WORKDIR /work
ENTRYPOINT ["valipar"]

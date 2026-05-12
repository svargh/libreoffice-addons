FROM ubuntu:24.04

ARG LO_VERSION=26.2.3
ARG LO_TARBALL_URL=

ENV DEBIAN_FRONTEND=noninteractive \
    DISPLAY=:1 \
    HOME=/tmp/libreoffice-rest-home \
    LIBREOFFICE_REST_DIR=/work/LibreOfficeRest \
    LIBREOFFICE_REST_LO_SETUP_DIR=/work/LibreOfficeRest/tmp/losetup \
    LIBREOFFICE_REST_DEBUG_LOG=/tmp/libreoffice-rest.log \
    SAL_USE_VCLPLUGIN=gen \
    GDK_BACKEND=x11 \
    QT_QPA_PLATFORM=xcb

WORKDIR /work/LibreOfficeRest

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        dbus-x11 \
        fonts-dejavu-core \
        libnss-wrapper \
        novnc \
        openbox \
        openjdk-17-jre-headless \
        procps \
        tar \
        websockify \
        x11-utils \
        x11vnc \
        xauth \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    mkdir -p "$LIBREOFFICE_REST_LO_SETUP_DIR"; \
    url="$LO_TARBALL_URL"; \
    if [ -z "$url" ]; then \
      url="https://download.documentfoundation.org/libreoffice/stable/${LO_VERSION}/deb/x86_64/LibreOffice_${LO_VERSION}_Linux_x86-64_deb.tar.gz"; \
    fi; \
    curl --fail --location --retry 5 --retry-delay 2 "$url" -o "$LIBREOFFICE_REST_LO_SETUP_DIR/libreoffice-linux-x86_64-deb.tar.gz"; \
    rm -rf "$LIBREOFFICE_REST_LO_SETUP_DIR/extracted"; \
    mkdir -p "$LIBREOFFICE_REST_LO_SETUP_DIR/extracted"; \
    tar -xzf "$LIBREOFFICE_REST_LO_SETUP_DIR/libreoffice-linux-x86_64-deb.tar.gz" \
      -C "$LIBREOFFICE_REST_LO_SETUP_DIR/extracted" --strip-components=1; \
    apt-get update; \
    apt-get install -y --no-install-recommends "$LIBREOFFICE_REST_LO_SETUP_DIR/extracted/DEBS/"*.deb; \
    soffice_path="$(find /opt -type f -path '/opt/libreoffice*/program/soffice' | sort | tail -n 1)"; \
    unopkg_path="$(find /opt -type f -path '/opt/libreoffice*/program/unopkg' | sort | tail -n 1)"; \
    test -x "$soffice_path"; \
    test -x "$unopkg_path"; \
    ln -sf "$soffice_path" /usr/local/bin/soffice; \
    ln -sf "$soffice_path" /usr/local/bin/libreoffice; \
    ln -sf "$unopkg_path" /usr/local/bin/unopkg; \
    soffice --version; \
    rm -rf /var/lib/apt/lists/*

COPY target/*.oxt /work/LibreOfficeRest/target/
COPY example-restapi.ods /work/LibreOfficeRest/example-restapi.ods
COPY docker/gui-vnc-start.sh /opt/libreoffice-rest/gui-vnc-start.sh

RUN chmod +x /opt/libreoffice-rest/gui-vnc-start.sh \
    && chmod -R a+rwX /work/LibreOfficeRest /tmp

EXPOSE 5901 6080
ENTRYPOINT ["/opt/libreoffice-rest/gui-vnc-start.sh"]

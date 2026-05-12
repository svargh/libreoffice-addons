# syntax=docker/dockerfile:1

# Debian-based Maven image. Do not use Alpine here because LibreOffice SDK
# packages are installed with apt.
ARG MAVEN_IMAGE=maven:3-eclipse-temurin-17
FROM ${MAVEN_IMAGE}

ENV DEBIAN_FRONTEND=noninteractive \
    OFFICE_PROGRAM_PATH=/usr/lib/libreoffice/program \
    UNO_PATH=/usr/lib/libreoffice/program \
    LO_SDK_HOME=/usr/lib/libreoffice/sdk \
    LO_TYPES_RDB=/usr/lib/libreoffice/program/types.rdb \
    LO_OFFAPI_RDB=/usr/lib/libreoffice/program/types/offapi.rdb \
    LO_IDL_DIR=/usr/lib/libreoffice/sdk/idl \
    MAVEN_OPTS="-Dfile.encoding=UTF-8"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        file \
        libxml2-utils \
        libreoffice \
        libreoffice-dev \
        libreoffice-java-common \
        procps \
        unzip \
        zip \
    && rm -rf /var/lib/apt/lists/* \
    && mvn -version \
    && test -d "$OFFICE_PROGRAM_PATH" \
    && test -d "$LO_SDK_HOME" \
    && test -f "$LO_TYPES_RDB" \
    && test -f "$LO_OFFAPI_RDB" \
    && find /usr/lib/libreoffice -type f \( -name 'unoidl-write' -o -name 'javamaker' -o -name 'idlc' -o -name 'regmerge' \) -print | sort

WORKDIR /work/LibreOfficeRest

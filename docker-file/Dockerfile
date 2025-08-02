FROM public.ecr.aws/lambda/provided:al2

# Install Java 17
RUN yum install -y java-17-amazon-corretto

ARG INCLUDE_MEDIAINFO
ARG HANDLER
ENV LAMBDA_HANDLER=$HANDLER

COPY target/amazon-0.0.1-SNAPSHOT.jar /var/task/amazon-0.0.1-SNAPSHOT.jar

# Conditionally include mediainfo
COPY bin/mediainfo /var/task/mediainfo

COPY docker-file/bootstrap /var/task/bootstrap

# 🔧 Fix line endings only if needed
RUN grep -q $'\r' /var/task/bootstrap && echo "Fixing CRLF line endings in bootstrap" && sed -i 's/\r$//' /var/task/bootstrap || echo "bootstrap has correct line endings"

# Only chmod if the binary is being used
RUN chmod +x /var/task/bootstrap && \
    if [ "$INCLUDE_MEDIAINFO" = "true" ]; then chmod +x /var/task/mediainfo; fi

# Use shell script as entrypoint so $HANDLER is evaluated at runtime
ENTRYPOINT ["/var/task/bootstrap"]

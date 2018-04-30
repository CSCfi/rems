FROM python:3-alpine

WORKDIR /usr/src

COPY mkdocs.yml .
COPY ./docs ./docs
RUN pip install mkdocs
RUN chgrp -R 0 /usr/src && \
    chmod -R g=u /usr/src

EXPOSE 8000
ENTRYPOINT ["sh", "-c", "mkdocs serve -a 0.0.0.0:8000"]

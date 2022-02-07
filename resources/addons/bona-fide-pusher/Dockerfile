FROM python:3.7-alpine

COPY requirements.txt /bf_pusher/
WORKDIR /bf_pusher

RUN pip install -r requirements.txt

COPY . /bf_pusher
EXPOSE 3008
CMD ["./bona_fide_pusher.py"]

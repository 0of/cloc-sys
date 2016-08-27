FROM ubuntu

MAINTAINER 0of version 1.0

RUN apt-get update && \
    apt-get install -y nodejs npm perl git cloc

RUN mkdir /opt/task
RUN cd /opt/task/; git clone https://github.com/0of/cloc-sys.git cloc
RUN cd /opt/task/cloc; npm install .

RUN mkdir /opt/Users/

ENTRYPOINT ["node" "/opt/task/cloc/index.js"]
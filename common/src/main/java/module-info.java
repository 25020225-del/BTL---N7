module common {
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.slf4j;
    opens model.auction to com.fasterxml.jackson.databind;

    exports network;
    exports utils;
    exports model.auction;
    exports model.base;
    exports model.finance;
    exports model.item;
    exports model.user;
}
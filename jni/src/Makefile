SRC      = $(wildcard *.cpp)
OBJ      = $(SRC:.cpp=.o)
CFLAGS = -Wno-sign-compare
CPPFLAGS = -Wall -Wno-sign-compare
CXXFLAGS = -I../dep/libsoldout -g -O3 -fPIC

.PHONY: all clean

all: $(OBJ)

%.o: %.cpp
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -c -o $@ $<

clean:
	rm -f *.o
	rm -f *.a
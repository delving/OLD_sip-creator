package main

/////////////////////////////////////////////////////////////////
//Code generated by chidley https://github.com/gnewton/chidley //
/////////////////////////////////////////////////////////////////

import (
	"bufio"
	"compress/bzip2"
	"compress/gzip"
	"encoding/json"
	"encoding/xml"
	"flag"
	"fmt"
	"io"
	"log"
	"math"
	"os"
	"strings"
)

const (
	JsonOut = iota
	XmlOut
	CountAll
)

var toJson bool = false
var toXml bool = false
var oneLevelDown bool = false
var countAll bool = false
var musage bool = false

var uniqueFlags = []*bool{
	&toJson,
	&toXml,
	&countAll}

var filenames = [1]string{"/home/kiivihal/src/kokoro/narthex-sip-creator/sip-core/src/test/resources/edm_5.2.6_record-definition.xml"}

func init() {

	flag.BoolVar(&toJson, "j", toJson, "Convert to JSON")
	flag.BoolVar(&toXml, "x", toXml, "Convert to XML")
	flag.BoolVar(&countAll, "c", countAll, "Count each instance of XML tags")
	flag.BoolVar(&oneLevelDown, "s", oneLevelDown, "Stream XML by using XML elements one down from the root tag. Good for huge XML files (see http://blog.davidsingleton.org/parsing-huge-xml-files-with-go/")
	flag.BoolVar(&musage, "h", musage, "Usage")
	//flag.StringVar(&filename, "f", filename, "XML file or URL to read in")

	flag.Int64Var(&recordLimit, "n", recordLimit, "Limit # records output")
}

var out int = -1

var counters map[string]*int

var recordLimit int64 = int64(math.MaxInt64)
var recordCounter = int64(0)

func main() {
	flag.Parse()

	if musage {
		flag.Usage()
		return
	}

	numSetBools, outFlag := numberOfBoolsSet(uniqueFlags)
	if numSetBools == 0 {
		flag.Usage()
		return
	}

	if numSetBools != 1 {
		flag.Usage()
		log.Fatal("Only one of ", uniqueFlags, " can be set at once")
	}

	counter := 0
	stop := false
	counters = make(map[string]*int)
	for i, _ := range filenames {
		filename := filenames[i]
		reader, xmlFile, err := genericReader(filename)
		if err != nil {
			log.Fatal(err)
			return
		}

		decoder := xml.NewDecoder(reader)

		for {
			if stop {
				break
			}
			token, _ := decoder.Token()
			if token == nil {
				break
			}
			switch se := token.(type) {
			case xml.StartElement:
				counter++
				handleFeed(se, decoder, outFlag)
				if recordCounter == recordLimit {
					stop = true
				}
			}

		}
		if stop {
			break
		}
		if xmlFile != nil {
			defer xmlFile.Close()
		}
	}
	if countAll {
		for k, v := range counters {
			fmt.Println(*v, k)
		}
	}
}

func handleFeed(se xml.StartElement, decoder *xml.Decoder, outFlag *bool) {
	if outFlag == &countAll {
		incrementCounter(se.Name.Space, se.Name.Local)
	} else {
		if !oneLevelDown {
			if se.Name.Local == "record-definition" && se.Name.Space == "" {
				var item Crecord_dash_definition
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}
		} else {

			if se.Name.Local == "attrs" && se.Name.Space == "" {
				recordCounter++
				var item Cattrs
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}

			if se.Name.Local == "root" && se.Name.Space == "" {
				recordCounter++
				var item Croot
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}

			if se.Name.Local == "field-markers" && se.Name.Space == "" {
				recordCounter++
				var item Cfield_dash_markers
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}

			if se.Name.Local == "opts" && se.Name.Space == "" {
				recordCounter++
				var item Copts
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}

			if se.Name.Local == "docs" && se.Name.Space == "" {
				recordCounter++
				var item Cdocs
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}

			if se.Name.Local == "namespaces" && se.Name.Space == "" {
				recordCounter++
				var item Cnamespaces
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}

			if se.Name.Local == "functions" && se.Name.Space == "" {
				recordCounter++
				var item Cfunctions
				decoder.DecodeElement(&item, &se)
				switch outFlag {
				case &toJson:
					writeJson(item)
				case &toXml:
					writeXml(item)
				}
			}

		}
	}
}

func makeKey(space string, local string) string {
	if space == "" {
		space = "_"
	}
	return space + ":" + local
}

func incrementCounter(space string, local string) {
	key := makeKey(space, local)

	counter, ok := counters[key]
	if !ok {
		n := 1
		counters[key] = &n
	} else {
		newv := *counter + 1
		counters[key] = &newv
	}
}

func writeJson(item interface{}) {
	b, err := json.MarshalIndent(item, "", " ")
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(string(b))
}

func writeXml(item interface{}) {
	output, err := xml.MarshalIndent(item, "  ", "    ")
	if err != nil {
		fmt.Printf("error: %v\n", err)
	}
	os.Stdout.Write(output)
}

func genericReader(filename string) (io.Reader, *os.File, error) {
	if filename == "" {
		return bufio.NewReader(os.Stdin), nil, nil
	}
	file, err := os.Open(filename)
	if err != nil {
		return nil, nil, err
	}
	if strings.HasSuffix(filename, "bz2") {
		return bufio.NewReader(bzip2.NewReader(bufio.NewReader(file))), file, err
	}

	if strings.HasSuffix(filename, "gz") {
		reader, err := gzip.NewReader(bufio.NewReader(file))
		if err != nil {
			return nil, nil, err
		}
		return bufio.NewReader(reader), file, err
	}
	return bufio.NewReader(file), file, err
}

func numberOfBoolsSet(a []*bool) (int, *bool) {
	var setBool *bool
	counter := 0
	for i := 0; i < len(a); i++ {
		if *a[i] {
			counter += 1
			setBool = a[i]
		}
	}
	return counter, setBool
}

///////////////////////////
/// structs
///////////////////////////

type Cattr struct {
	XMLName            xml.Name            `xml:"attr,omitempty" json:"attr,omitempty"`
	Attrtag            string              `xml:"tag,attr"  json:",omitempty"`
	AttruriCheck       string              `xml:"uriCheck,attr"  json:",omitempty"`
	Cnode_dash_mapping *Cnode_dash_mapping `xml:"node-mapping,omitempty" json:"node-mapping,omitempty"`
}

type Cattrs struct {
	XMLName xml.Name `xml:"attrs,omitempty" json:"attrs,omitempty"`
	Cattr   []*Cattr `xml:"attr,omitempty" json:"attr,omitempty"`
}

type Cdoc struct {
	XMLName  xml.Name `xml:"doc,omitempty" json:"doc,omitempty"`
	Attrpath string   `xml:"path,attr"  json:",omitempty"`
	Cpara    []*Cpara `xml:"para,omitempty" json:"para,omitempty"`
}

type Cdocs struct {
	XMLName xml.Name `xml:"docs,omitempty" json:"docs,omitempty"`
	Cdoc    []*Cdoc  `xml:"doc,omitempty" json:"doc,omitempty"`
}

type Celem struct {
	XMLName            xml.Name            `xml:"elem,omitempty" json:"elem,omitempty"`
	Attrattribute      string              `xml:"attribute,attr"  json:",omitempty"`
	Attrattrs          string              `xml:"attrs,attr"  json:",omitempty"`
	Attrfunction       string              `xml:"function,attr"  json:",omitempty"`
	Attrhidden         string              `xml:"hidden,attr"  json:",omitempty"`
	Attrtag            string              `xml:"tag,attr"  json:",omitempty"`
	Attrunmappable     string              `xml:"unmappable,attr"  json:",omitempty"`
	AttruriCheck       string              `xml:"uriCheck,attr"  json:",omitempty"`
	Cattr              []*Cattr            `xml:"attr,omitempty" json:"attr,omitempty"`
	Celem              []*Celem            `xml:"elem,omitempty" json:"elem,omitempty"`
	Cnode_dash_mapping *Cnode_dash_mapping `xml:"node-mapping,omitempty" json:"node-mapping,omitempty"`
}

type Cfield_dash_markers struct {
	XMLName xml.Name `xml:"field-markers,omitempty" json:"field-markers,omitempty"`
}

type Cfunctions struct {
	XMLName                xml.Name                  `xml:"functions,omitempty" json:"functions,omitempty"`
	Cmapping_dash_function []*Cmapping_dash_function `xml:"mapping-function,omitempty" json:"mapping-function,omitempty"`
}

type Cgroovy_dash_code struct {
	XMLName xml.Name   `xml:"groovy-code,omitempty" json:"groovy-code,omitempty"`
	Cstring []*Cstring `xml:"string,omitempty" json:"string,omitempty"`
}

type CisRequiredBy struct {
	XMLName xml.Name `xml:"isRequiredBy,omitempty" json:"isRequiredBy,omitempty"`
	Text    string   `xml:",chardata" json:",omitempty"`
}

type Cmapping_dash_function struct {
	XMLName            xml.Name            `xml:"mapping-function,omitempty" json:"mapping-function,omitempty"`
	Attrname           string              `xml:"name,attr"  json:",omitempty"`
	Cgroovy_dash_code  *Cgroovy_dash_code  `xml:"groovy-code,omitempty" json:"groovy-code,omitempty"`
	Csample_dash_input *Csample_dash_input `xml:"sample-input,omitempty" json:"sample-input,omitempty"`
}

type Cnamespace struct {
	XMLName    xml.Name `xml:"namespace,omitempty" json:"namespace,omitempty"`
	Attrprefix string   `xml:"prefix,attr"  json:",omitempty"`
	Attruri    string   `xml:"uri,attr"  json:",omitempty"`
}

type Cnamespaces struct {
	XMLName    xml.Name      `xml:"namespaces,omitempty" json:"namespaces,omitempty"`
	Cnamespace []*Cnamespace `xml:"namespace,omitempty" json:"namespace,omitempty"`
}

type Cnode_dash_mapping struct {
	XMLName           xml.Name           `xml:"node-mapping,omitempty" json:"node-mapping,omitempty"`
	AttrinputPath     string             `xml:"inputPath,attr"  json:",omitempty"`
	Cgroovy_dash_code *Cgroovy_dash_code `xml:"groovy-code,omitempty" json:"groovy-code,omitempty"`
}

type Copt struct {
	XMLName   xml.Name `xml:"opt,omitempty" json:"opt,omitempty"`
	Attrvalue string   `xml:"value,attr"  json:",omitempty"`
}

type Copt_dash_list struct {
	XMLName         xml.Name `xml:"opt-list,omitempty" json:"opt-list,omitempty"`
	Attrdictionary  string   `xml:"dictionary,attr"  json:",omitempty"`
	AttrdisplayName string   `xml:"displayName,attr"  json:",omitempty"`
	Attrpath        string   `xml:"path,attr"  json:",omitempty"`
	Copt            []*Copt  `xml:"opt,omitempty" json:"opt,omitempty"`
}

type Copts struct {
	XMLName        xml.Name          `xml:"opts,omitempty" json:"opts,omitempty"`
	Copt_dash_list []*Copt_dash_list `xml:"opt-list,omitempty" json:"opt-list,omitempty"`
}

type Cpara struct {
	XMLName       xml.Name       `xml:"para,omitempty" json:"para,omitempty"`
	Attrname      string         `xml:"name,attr"  json:",omitempty"`
	CisRequiredBy *CisRequiredBy `xml:"isRequiredBy,omitempty" json:"isRequiredBy,omitempty"`
	Text          string         `xml:",chardata" json:",omitempty"`
}

type Crecord_dash_definition struct {
	XMLName             xml.Name             `xml:"record-definition,omitempty" json:"record-definition,omitempty"`
	Attrflat            string               `xml:"flat,attr"  json:",omitempty"`
	Attrprefix          string               `xml:"prefix,attr"  json:",omitempty"`
	Attrversion         string               `xml:"version,attr"  json:",omitempty"`
	Cattrs              *Cattrs              `xml:"attrs,omitempty" json:"attrs,omitempty"`
	Cdocs               *Cdocs               `xml:"docs,omitempty" json:"docs,omitempty"`
	Cfield_dash_markers *Cfield_dash_markers `xml:"field-markers,omitempty" json:"field-markers,omitempty"`
	Cfunctions          *Cfunctions          `xml:"functions,omitempty" json:"functions,omitempty"`
	Cnamespaces         *Cnamespaces         `xml:"namespaces,omitempty" json:"namespaces,omitempty"`
	Copts               *Copts               `xml:"opts,omitempty" json:"opts,omitempty"`
	Croot               *Croot               `xml:"root,omitempty" json:"root,omitempty"`
}

type Croot struct {
	XMLName            xml.Name            `xml:"root,omitempty" json:"root,omitempty"`
	Attrtag            string              `xml:"tag,attr"  json:",omitempty"`
	Celem              []*Celem            `xml:"elem,omitempty" json:"elem,omitempty"`
	Cnode_dash_mapping *Cnode_dash_mapping `xml:"node-mapping,omitempty" json:"node-mapping,omitempty"`
}

type Csample_dash_input struct {
	XMLName xml.Name   `xml:"sample-input,omitempty" json:"sample-input,omitempty"`
	Cstring []*Cstring `xml:"string,omitempty" json:"string,omitempty"`
}

type Cstring struct {
	XMLName xml.Name `xml:"string,omitempty" json:"string,omitempty"`
	Text    string   `xml:",chardata" json:",omitempty"`
}

///////////////////////////
